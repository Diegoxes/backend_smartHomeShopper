package com.smarthome.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final ProductRepository productRepo;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final ProductSemanticMatchService semanticMatch;
    private final WhatsAppClarificationService clarification;
    private final WhatsAppInventoryActionService actions;

    @Value("${deepseek.api-key}")
    private String deepseekApiKey;

    @Value("${deepseek.base-url}")
    private String deepseekBaseUrl;

    @Value("${deepseek.model}")
    private String deepseekModel;

    public String parseWhatsAppMessage(User user, String orgId, String rawMessage) {
        String systemPrompt = """
            Eres un asistente de inventario B2B. Analiza el mensaje y extrae productos.
            Responde SOLO con un JSON con este formato (sin markdown, sin explicaciones):
            {
              "action": "add" | "consume" | "query" | "unknown",
              "items": [
                { "name": "nombre del producto", "quantity": 1.0, "unit": "UNIT|KG|LITER|GRAM|ML|PACK" }
              ],
              "reply": "respuesta amigable en español confirmando la acción"
            }
            Reglas:
            - "add"/"compré"/"tengo" → action: add
            - "usé"/"gasté"/"consumí" → action: consume
            - "cuánto tengo"/"inventario" → action: query
            - Si no entiendes → action: unknown
            """;

        try {
            String raw = callDeepSeekChat(systemPrompt, rawMessage);
            String jsonResponse = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(jsonResponse);

            String action = root.path("action").asText("unknown");
            String reply = root.path("reply").asText("Entendido.");
            JsonNode items = root.path("items");

            if (!("add".equals(action) || "consume".equals(action)) || !items.isArray() || items.isEmpty()) {
                return reply;
            }

            boolean singleSemantic = items.size() == 1;

            for (JsonNode item : items) {
                String name = item.path("name").asText();
                double quantity = item.path("quantity").asDouble(1.0);
                String unitStr = item.path("unit").asText("UNIT");
                Product.UnitType unit = WhatsAppAiSupport.safeUnit(unitStr);

                ProductSemanticMatchService.MatchResult res =
                        semanticMatch.resolve(orgId, name, singleSemantic);

                if (singleSemantic && res instanceof ProductSemanticMatchService.MatchFuzzy mf) {
                    var cands = mf.candidates();
                    if (cands != null && !cands.isEmpty()) {
                        var lines = cands.stream().limit(5).map(c -> {
                                    var line = new WhatsAppClarificationService.CandidateLine();
                                    line.setProductId(c.getId());
                                    line.setLabel(c.getLabel());
                                    line.setScore(c.getScore());
                                    return line;
                                })
                                .collect(Collectors.toList());

                        var payload = new WhatsAppClarificationService.ClarificationPayload();
                        payload.setKind("llm_item");
                        payload.setAction(action);
                        payload.setOriginalName(name);
                        payload.setQuantity(quantity);
                        payload.setUnit(unitStr);
                        return clarification.savePendingAndReply(user, payload, lines);
                    }
                }

                Optional<Product> target = Optional.empty();
                if (res instanceof ProductSemanticMatchService.MatchExact mex) {
                    target = Optional.of(mex.product());
                } else {
                    target = legacyEquals(orgId, name);
                }

                if ("consume".equals(action)) {
                    target.ifPresent(p -> actions.consume(user, p, quantity));
                } else {
                    Product existing = target.orElse(null);
                    actions.addOrRestock(user, unit, quantity,
                            existing != null ? existing.getName() : name,
                            existing);
                }
            }

            return reply;

        } catch (Exception e) {
            log.error("DeepSeek parsing failed: {}", e.getMessage());
            return "No pude entender tu mensaje. Prueba: \"Compré 2 leches y 1kg de arroz\"";
        }
    }

    private Optional<Product> legacyEquals(String orgId, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return productRepo.findByOrganizationId(orgId).stream()
                .filter(p -> p.getName().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private String callDeepSeekChat(String system, String userMsg) {
        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            throw new IllegalStateException("deepseek.api-key / DEEPSEEK_API_KEY no configurada");
        }
        String base = deepseekBaseUrl.replaceAll("/+$", "");
        String url = base + "/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepseekApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", deepseekModel);
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", userMsg)
        ));

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        Map<String, Object> respBody = response.getBody();
        if (respBody == null) {
            throw new IllegalStateException("Respuesta vacía de DeepSeek");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("choices vacío en respuesta DeepSeek");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        Object content = msg != null ? msg.get("content") : null;
        if (content == null) {
            throw new IllegalStateException("message.content ausente");
        }
        return content.toString().trim();
    }

    /** Recorta fences ```json ... ``` si el modelo los incluye. */
    private static String extractJsonObject(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('{');
            int end = s.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return s.substring(start, end + 1);
            }
        }
        return s;
    }
}
