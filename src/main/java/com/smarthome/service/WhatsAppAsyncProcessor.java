package com.smarthome.service;

import com.smarthome.entity.User;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppAsyncProcessor {

    private final UserRepository userRepository;
    private final TwilioMediaService twilioMediaService;
    private final BusinessAssistantService businessAssistant;
    private final TwilioWhatsAppOutboundService outbound;

    @Async
    public void processImageMessage(String userId, String orgId, String toWhatsApp,
                                    String caption, String mediaUrl, String mimeType) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Usuario {} no encontrado para procesar imagen WhatsApp", userId);
            return;
        }
        try {
            if (!twilioMediaService.isSupportedImageMime(mimeType)) {
                outbound.sendText(toWhatsApp,
                        "Solo puedo analizar imágenes JPEG, PNG, WebP o GIF. Envía una foto o escribe tu consulta.");
                return;
            }
            byte[] imageBytes = twilioMediaService.downloadMedia(mediaUrl);
            String reply = businessAssistant.parseWhatsAppMessageWithImage(
                    user, orgId, caption, imageBytes, mimeType);
            outbound.sendText(toWhatsApp, reply);
        } catch (Exception e) {
            log.warn("Error procesando imagen WhatsApp async: {}", e.getMessage());
            outbound.sendText(toWhatsApp,
                    "No pude procesar la imagen. Intenta de nuevo o escribe tu consulta en texto.");
        }
    }

    @Async
    public void processAiTextMessage(String userId, String orgId, String toWhatsApp, String body) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Usuario {} no encontrado para procesar texto IA WhatsApp", userId);
            return;
        }
        try {
            String reply = businessAssistant.parseWhatsAppMessage(user, orgId, body != null ? body : "");
            outbound.sendText(toWhatsApp, reply);
        } catch (Exception e) {
            log.warn("Error procesando texto IA WhatsApp async: {}", e.getMessage());
            outbound.sendText(toWhatsApp,
                    "No pude procesar tu mensaje. Prueba con *inventario* o *ayuda*.");
        }
    }
}
