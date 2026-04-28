package com.smarthome.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.entity.ConsumptionLog;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.ConsumptionLogRepository;
import com.smarthome.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final ProductRepository productRepo;
    private final ConsumptionLogRepository logRepo;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${deepseek.api-key}")
    private String deepseekApiKey;

    @Value("${deepseek.base-url}")
    private String deepseekBaseUrl;

    @Value("${deepseek.model}")
    private String deepseekModel;

    public String parseWhatsAppMessage(User user, String message) {
        String systemPrompt = """
            Eres un asistente de inventario del hogar. Analiza el mensaje del usuario y extrae los productos mencionados.
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
            String raw = callDeepSeekChat(systemPrompt, message);
            String jsonResponse = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(jsonResponse);

            String action = root.path("action").asText("unknown");
            String reply  = root.path("reply").asText("Entendido.");
            JsonNode items = root.path("items");

            if (("add".equals(action) || "consume".equals(action)) && items.isArray()) {
                List<Product> userProducts = productRepo.findByUserId(user.getId());
                for (JsonNode item : items) {
                    String name     = item.path("name").asText();
                    double quantity = item.path("quantity").asDouble(1.0);
                    String unitStr  = item.path("unit").asText("UNIT");
                    Product.UnitType unit = safeUnit(unitStr);

                    Optional<Product> existing = userProducts.stream()
                            .filter(p -> p.getName().equalsIgnoreCase(name))
                            .findFirst();

                    if ("add".equals(action)) {
                        if (existing.isPresent()) {
                            Product p = existing.get();
                            p.setQuantity(p.getQuantity() + quantity);
                            productRepo.save(p);
                            logRepo.save(log(p, quantity, ConsumptionLog.ActionType.RESTOCKED));
                        } else {
                            Product p = Product.builder()
                                    .user(user).name(name)
                                    .quantity(quantity).minQuantity(1.0)
                                    .unit(unit).consumptionPerUse(1.0)
                                    .build();
                            productRepo.save(p);
                        }
                    } else if ("consume".equals(action)) {
                        existing.ifPresent(p -> {
                            p.setQuantity(Math.max(0, p.getQuantity() - quantity));
                            productRepo.save(p);
                            logRepo.save(log(p, -quantity, ConsumptionLog.ActionType.CONSUMED));
                        });
                    }
                }
            }
            return reply;

        } catch (Exception e) {
            log.error("DeepSeek parsing failed: {}", e.getMessage());
            return "No pude entender tu mensaje. Prueba: \"Compré 2 leches y 1kg de arroz\"";
        }
    }

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

    private Product.UnitType safeUnit(String s) {
        try { return Product.UnitType.valueOf(s.toUpperCase()); }
        catch (Exception e) { return Product.UnitType.UNIT; }
    }

    private ConsumptionLog log(Product p, double qty, ConsumptionLog.ActionType type) {
        return ConsumptionLog.builder()
                .product(p).quantityChange(qty)
                .actionType(type)
                .source(ConsumptionLog.Source.WHATSAPP)
                .build();
    }
}
