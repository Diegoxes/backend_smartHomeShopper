package com.smarthome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.ProductRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessAssistantServiceTest {

    @Mock ProductRepository productRepo;
    @Mock FoundryResponsesClient foundryClient;
    @Mock BusinessContextService businessContext;
    @Mock ProductSemanticMatchService semanticMatch;
    @Mock WhatsAppClarificationService clarification;
    @Mock WhatsAppInventoryActionService actions;

    private BusinessAssistantService assistantService;

    @BeforeEach
    void setUp() {
        assistantService = new BusinessAssistantService(
                productRepo,
                new ObjectMapper(),
                foundryClient,
                businessContext,
                semanticMatch,
                clarification,
                actions);
        when(businessContext.buildContextForOrg(anyString())).thenReturn("Organización: Test Org");
    }

    @Test
    void parseWhatsAppMessage_queryAction_returnsReplyOnly() {
        User user = TestFixtures.user();
        String json = """
                {"action":"query","items":[],"reply":"Tienes 10 productos"}
                """;
        when(foundryClient.completeText(anyString(), eq("cuánto tengo"))).thenReturn(json);

        String result = assistantService.parseWhatsAppMessage(user, TestFixtures.ORG_ID, "cuánto tengo");

        assertEquals("Tienes 10 productos", result);
        verifyNoInteractions(actions);
    }

    @Test
    void parseWhatsAppMessage_consumeExact_executesAction() {
        User user = TestFixtures.user();
        Product product = TestFixtures.product();
        String json = """
                {"action":"consume","items":[{"name":"Producto Test","quantity":1,"unit":"UNIT"}],"reply":"Listo"}
                """;
        when(foundryClient.completeText(anyString(), anyString())).thenReturn(json);
        when(semanticMatch.resolve(TestFixtures.ORG_ID, "Producto Test", true))
                .thenReturn(new ProductSemanticMatchService.MatchExact(product));

        String result = assistantService.parseWhatsAppMessage(user, TestFixtures.ORG_ID, "consumí producto test");

        assertEquals("Listo", result);
        verify(actions).consume(user, product, 1.0);
    }

    @Test
    void parseWhatsAppMessageWithImage_usesMultimodal() {
        User user = TestFixtures.user();
        byte[] image = new byte[]{1, 2, 3};
        String json = """
                {"action":"query","items":[],"reply":"Veo un ticket de compra"}
                """;
        when(foundryClient.completeMultimodal(anyString(), eq("foto ticket"), eq(image), eq("image/jpeg")))
                .thenReturn(json);

        String result = assistantService.parseWhatsAppMessageWithImage(
                user, TestFixtures.ORG_ID, "foto ticket", image, "image/jpeg");

        assertEquals("Veo un ticket de compra", result);
        verify(foundryClient).completeMultimodal(anyString(), eq("foto ticket"), eq(image), eq("image/jpeg"));
    }

    @Test
    void parseWhatsAppMessage_apiFailure_returnsFriendlyMessage() {
        when(foundryClient.completeText(anyString(), anyString()))
                .thenThrow(new RuntimeException("network error"));

        String result = assistantService.parseWhatsAppMessage(
                TestFixtures.user(), TestFixtures.ORG_ID, "hola");

        assertTrue(result.contains("No pude entender"));
    }
}
