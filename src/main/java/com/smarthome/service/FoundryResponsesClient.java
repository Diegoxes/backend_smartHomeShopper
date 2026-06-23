package com.smarthome.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smarthome.config.FoundryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoundryResponsesClient {

    private final FoundryProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public String completeText(String instructions, String userMessage) {
        ObjectNode body = baseRequest(instructions);
        body.put("input", userMessage);
        return callFoundry(body);
    }

    public String completeMultimodal(String instructions, String userText, byte[] imageBytes, String mimeType) {
        String format = mimeToFormat(mimeType);
        String dataUrl = "data:image/" + format + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes);

        ObjectNode body = baseRequest(instructions);

        ArrayNode content = objectMapper.createArrayNode();
        content.addObject().put("type", "input_text").put("text", userText);
        content.addObject()
                .put("type", "input_image")
                .put("image_url", dataUrl);

        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.set("content", content);

        ArrayNode input = objectMapper.createArrayNode();
        input.add(userMessage);
        body.set("input", input);

        return callFoundry(body);
    }

    private ObjectNode baseRequest(String instructions) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Azure AI Foundry no configurado (base-url, api-key, deployment)");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getDeployment());
        body.put("instructions", instructions);
        body.put("temperature", properties.getTemperature());
        return body;
    }

    private String callFoundry(ObjectNode body) {
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/responses";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", properties.getApiKey());

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body.toString(), headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Foundry respondió con HTTP " + response.getStatusCode());
        }

        try {
            return extractOutputText(objectMapper.readTree(response.getBody()));
        } catch (Exception e) {
            log.error("No se pudo parsear respuesta Foundry: {}", e.getMessage());
            throw new IllegalStateException("Respuesta Foundry ilegible", e);
        }
    }

    private String extractOutputText(JsonNode root) {
        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : output) {
                appendTextFromNode(item, sb);
            }
            if (!sb.isEmpty()) {
                return sb.toString().trim();
            }
        }

        JsonNode text = root.path("output_text");
        if (text.isTextual()) {
            return text.asText().trim();
        }

        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isTextual()) {
                return content.asText().trim();
            }
        }

        throw new IllegalStateException("No se encontró texto en la respuesta Foundry");
    }

    private void appendTextFromNode(JsonNode node, StringBuilder sb) {
        JsonNode content = node.path("content");
        if (content.isArray()) {
            for (JsonNode part : content) {
                String type = part.path("type").asText("");
                if (type.contains("text") && part.has("text")) {
                    sb.append(part.path("text").asText());
                }
            }
        }
        if (node.has("text")) {
            sb.append(node.path("text").asText());
        }
    }

    private static String mimeToFormat(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "jpeg";
        }
        return switch (mimeType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpeg";
        };
    }
}
