package com.smarthome.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessAssistantService {

    private static final String INTENT_PROMPT = """
            Eres un asistente de inventario B2B para WhatsApp. Analiza el mensaje del usuario (y opcionalmente una imagen).
            Responde SOLO con un JSON (sin markdown):
            {
              "action": "add" | "consume" | "query" | "unknown",
              "items": [
                { "name": "nombre del producto", "quantity": 1.0, "unit": "UNIT|KG|LITER|GRAM|ML|PACK" }
              ],
              "reply": "respuesta amigable en español"
            }
            Reglas:
            - "add"/"compré"/"tengo"/"entrada" → action: add
            - "usé"/"gasté"/"consumí"/"salida" → action: consume
            - preguntas sobre stock, alertas, valor, catálogo → action: query (items vacío; usa el contexto del negocio en reply)
            - Si no entiendes → action: unknown
            - Para imágenes: identifica productos, cantidades o tickets de compra si es posible
            """;

    private final ProductRepository productRepo;
    private final ObjectMapper objectMapper;
    private final FoundryResponsesClient foundryClient;
    private final BusinessContextService businessContext;
    private final ProductSemanticMatchService semanticMatch;
    private final WhatsAppClarificationService clarification;
    private final WhatsAppInventoryActionService actions;

    public String parseWhatsAppMessage(User user, String orgId, String rawMessage) {
        return process(user, orgId, rawMessage, null, null);
    }

    public String parseWhatsAppMessageWithImage(User user, String orgId, String caption,
                                                  byte[] imageBytes, String mimeType) {
        String text = caption != null && !caption.isBlank()
                ? caption
                : "Analiza esta imagen en el contexto de mi inventario.";
        return process(user, orgId, text, imageBytes, mimeType);
    }

    private String process(User user, String orgId, String userText,
                           byte[] imageBytes, String mimeType) {
        try {
            String context = businessContext.buildContextForOrg(orgId);
            String instructions = INTENT_PROMPT + "\n\nContexto del negocio:\n" + context;

            String raw = imageBytes != null && imageBytes.length > 0
                    ? foundryClient.completeMultimodal(instructions, userText, imageBytes, mimeType)
                    : foundryClient.completeText(instructions, userText);

            String jsonResponse = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(jsonResponse);

            String action = root.path("action").asText("unknown");
            String reply = root.path("reply").asText("Entendido.");
            JsonNode items = root.path("items");

            if ("query".equals(action) || "unknown".equals(action)
                    || !("add".equals(action) || "consume".equals(action))
                    || !items.isArray() || items.isEmpty()) {
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
                        if (lines.size() == 1) {
                            return clarification.saveYesNoConfirmAndReply(user, payload, lines.get(0));
                        }
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
            log.error("Foundry parsing failed: {}", e.getMessage());
            return "No pude entender tu mensaje. Prueba: \"Compré 2 leches y 1kg de arroz\"";
        }
    }

    private Optional<Product> legacyEquals(String orgId, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return productRepo.findByOrganizationId(orgId).stream()
                .filter(p -> p.getName().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

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
