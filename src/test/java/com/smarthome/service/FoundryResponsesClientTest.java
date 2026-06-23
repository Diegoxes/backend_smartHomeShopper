package com.smarthome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.config.FoundryProperties;
import com.smarthome.support.TestFixtures;
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
class FoundryResponsesClientTest {

    @Mock RestTemplate restTemplate;

    private FoundryResponsesClient client;

    @BeforeEach
    void setUp() {
        FoundryProperties props = new FoundryProperties();
        props.setBaseUrl("https://example.services.ai.azure.com/openai/v1");
        props.setApiKey("test-key");
        props.setDeployment("gpt-4o");
        client = new FoundryResponsesClient(props, restTemplate, new ObjectMapper());
    }

    @Test
    void completeText_extractsOutputText() {
        String body = """
                {"output":[{"content":[{"type":"output_text","text":"Hola mundo"}]}]}
                """;
        when(restTemplate.exchange(
                eq("https://example.services.ai.azure.com/openai/v1/responses"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));

        String result = client.completeText("instrucciones", "hola");

        assertEquals("Hola mundo", result);
    }

    @Test
    void completeText_notConfigured_throws() {
        FoundryProperties empty = new FoundryProperties();
        FoundryResponsesClient unconfigured = new FoundryResponsesClient(
                empty, restTemplate, new ObjectMapper());

        assertThrows(IllegalStateException.class,
                () -> unconfigured.completeText("x", "y"));
    }
}
