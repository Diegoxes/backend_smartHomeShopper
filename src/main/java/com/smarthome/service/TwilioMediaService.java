package com.smarthome.service;

import com.smarthome.config.TwilioProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class TwilioMediaService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");

    private final TwilioProperties twilioProperties;
    private final RestTemplate restTemplate;

    public byte[] downloadMedia(String mediaUrl) {
        if (!twilioProperties.hasMediaCredentials()) {
            throw new IllegalStateException("Credenciales Twilio no configuradas para descargar media");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(twilioProperties.getAccountSid(), twilioProperties.getAuthToken());

        ResponseEntity<byte[]> response = restTemplate.exchange(
                mediaUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );

        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("Media vacío desde Twilio");
        }
        if (body.length > MAX_BYTES) {
            throw new IllegalArgumentException("La imagen supera el límite de 5 MB");
        }
        return body;
    }

    public boolean isSupportedImageMime(String mimeType) {
        return mimeType != null && ALLOWED_MIME.contains(mimeType.toLowerCase());
    }
}
