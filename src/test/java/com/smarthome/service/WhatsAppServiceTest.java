package com.smarthome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.dto.WhatsAppReply;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock OrganizationMemberRepository memberRepository;
    @Mock OrganizationSettingsRepository settingsRepository;
    @Mock ProductRepository productRepo;
    @Mock WhatsAppAsyncProcessor asyncProcessor;
    @Mock WhatsAppClarificationService clarification;
    @Mock ProductSemanticMatchService semanticMatch;
    @Mock WhatsAppInventoryActionService actions;
    @Mock ReportExportService reportExportService;
    @Mock WhatsAppReportTokenService reportTokenService;
    @InjectMocks WhatsAppService whatsAppService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(whatsAppService, "aiEnabled", false);
    }

    @Test
    void handleIncoming_unregisteredPhone() {
        when(memberRepository.findByUserWhatsappNumber("521234567890")).thenReturn(Optional.empty());

        WhatsAppReply reply = whatsAppService.handleIncoming("521234567890", "hola");

        assertTrue(reply.body().contains("No encontramos tu número"));
    }

    @Test
    void handleIncoming_helpCommand() {
        stubMember();
        when(clarification.consumeReplyIfPending(any(), any())).thenReturn(Optional.empty());

        WhatsAppReply reply = whatsAppService.handleIncoming("521234567890", "ayuda");

        assertTrue(reply.body().contains("Comandos disponibles"));
    }

    @Test
    void handleIncoming_inventoryCommand() {
        stubMember();
        when(clarification.consumeReplyIfPending(any(), any())).thenReturn(Optional.empty());
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of(TestFixtures.product()));

        WhatsAppReply reply = whatsAppService.handleIncoming("521234567890", "inventario");

        assertTrue(reply.body().contains("Producto Test"));
    }

    @Test
    void handleIncoming_aiDisabled_returnsHelp() {
        stubMember();
        when(clarification.consumeReplyIfPending(any(), any())).thenReturn(Optional.empty());

        WhatsAppReply reply = whatsAppService.handleIncoming("521234567890", "compré 5 leche");

        assertTrue(reply.body().contains("Comandos disponibles"));
        verifyNoInteractions(asyncProcessor);
    }

    @Test
    void handleIncoming_imageWithAiEnabled_acknowledgesAndQueuesAsync() {
        ReflectionTestUtils.setField(whatsAppService, "aiEnabled", true);
        stubMember();
        when(clarification.consumeReplyIfPending(any(), any())).thenReturn(Optional.empty());

        WhatsAppReply reply = whatsAppService.handleIncoming(
                "521234567890", "foto", 1, "https://media.twilio.com/img.jpg", "image/jpeg");

        assertTrue(reply.body().contains("analizando"));
        verify(asyncProcessor).processImageMessage(
                eq(TestFixtures.user().getId()),
                eq(TestFixtures.ORG_ID),
                eq("521234567890"),
                eq("foto"),
                eq("https://media.twilio.com/img.jpg"),
                eq("image/jpeg"));
    }

    @Test
    void handleIncoming_aiEnabled_queuesTextProcessing() {
        ReflectionTestUtils.setField(whatsAppService, "aiEnabled", true);
        stubMember();
        when(clarification.consumeReplyIfPending(any(), any())).thenReturn(Optional.empty());

        WhatsAppReply reply = whatsAppService.handleIncoming("521234567890", "compré 5 leche");

        assertTrue(reply.body().contains("Procesando"));
        verify(asyncProcessor).processAiTextMessage(
                eq(TestFixtures.user().getId()),
                eq(TestFixtures.ORG_ID),
                eq("521234567890"),
                eq("compré 5 leche"));
    }

    private void stubMember() {
        OrganizationMember member = OrganizationMember.builder()
                .organization(TestFixtures.organization())
                .user(TestFixtures.user())
                .build();
        when(memberRepository.findByUserWhatsappNumber("521234567890")).thenReturn(Optional.of(member));
    }
}
