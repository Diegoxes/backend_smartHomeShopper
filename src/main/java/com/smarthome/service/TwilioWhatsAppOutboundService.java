package com.smarthome.service;

import com.smarthome.config.TwilioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioWhatsAppOutboundService {

    private final TwilioProperties twilioProperties;
    private final RestTemplate restTemplate;

    public void sendText(String toWhatsApp, String body) {
        if (!twilioProperties.hasMediaCredentials()) {
            log.warn("Twilio no configurado; no se puede enviar mensaje saliente a {}", toWhatsApp);
            return;
        }
        String url = "https://api.twilio.com/2010-04-01/Accounts/"
                + twilioProperties.getAccountSid() + "/Messages.json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(twilioProperties.getAccountSid(), twilioProperties.getAuthToken());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", normalizeWhatsApp(toWhatsApp));
        form.add("From", twilioProperties.getWhatsappFrom());
        form.add("Body", body);

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);
        } catch (Exception e) {
            log.error("Error enviando WhatsApp saliente a {}: {}", toWhatsApp, e.getMessage());
        }
    }

    private static String normalizeWhatsApp(String to) {
        if (to == null || to.isBlank()) return to;
        return to.startsWith("whatsapp:") ? to : "whatsapp:" + to;
    }
}
