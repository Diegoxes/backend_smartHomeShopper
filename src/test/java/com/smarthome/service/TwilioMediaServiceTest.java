package com.smarthome.service;

import com.smarthome.config.TwilioProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwilioMediaServiceTest {

    @Mock RestTemplate restTemplate;

    private TwilioMediaService mediaService;

    @BeforeEach
    void setUp() {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("AC123");
        props.setAuthToken("secret");
        mediaService = new TwilioMediaService(props, restTemplate);
    }

    @Test
    void downloadMedia_returnsBytes() {
        byte[] data = new byte[]{1, 2, 3};
        when(restTemplate.exchange(
                eq("https://api.twilio.com/media/1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(data));

        byte[] result = mediaService.downloadMedia("https://api.twilio.com/media/1");

        assertArrayEquals(data, result);
    }

    @Test
    void isSupportedImageMime_acceptsJpeg() {
        assertTrue(mediaService.isSupportedImageMime("image/jpeg"));
        assertFalse(mediaService.isSupportedImageMime("application/pdf"));
    }
}
