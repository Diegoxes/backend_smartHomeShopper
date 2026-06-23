package com.smarthome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.entity.User;
import com.smarthome.entity.WhatsAppPendingClarification;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.WhatsAppPendingClarificationRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppClarificationServiceTest {

    @Mock WhatsAppPendingClarificationRepository pendingRepo;
    @Mock ProductRepository productRepo;
    @Mock OrganizationMemberRepository memberRepository;
    @Mock WhatsAppInventoryActionService actions;
    @Mock ProductAliasService productAliasService;
    @Mock ObjectMapper objectMapper;
    @InjectMocks WhatsAppClarificationService clarificationService;

    @Test
    void consumeReplyIfPending_noPending_returnsEmpty() {
        User user = TestFixtures.user();
        when(pendingRepo.findActiveForUser(eq(user.getId()), any())).thenReturn(Optional.empty());

        assertTrue(clarificationService.consumeReplyIfPending(user, "1").isEmpty());
    }

    @Test
    void removePending_purgesForUser() {
        User user = TestFixtures.user();

        clarificationService.removePending(user);

        verify(pendingRepo).purgeForUser(user.getId());
    }

    @Test
    void saveYesNoConfirmAndReply_returnsSiNoPrompt() throws Exception {
        User user = TestFixtures.user();
        var payload = new WhatsAppClarificationService.ClarificationPayload();
        payload.setAction("add");
        payload.setOriginalName("inka kola");
        payload.setQuantity(10);
        var line = new WhatsAppClarificationService.CandidateLine();
        line.setProductId(TestFixtures.PRODUCT_ID);
        line.setLabel("Gaseosa Inka Kola");
        line.setScore(0.93);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        String reply = clarificationService.saveYesNoConfirmAndReply(user, payload, line);

        assertTrue(reply.contains("Gaseosa Inka Kola"));
        assertTrue(reply.toLowerCase().contains("sí"));
        assertTrue(reply.toLowerCase().contains("no"));
        verify(pendingRepo).save(any(WhatsAppPendingClarification.class));
    }

    @Test
    void savePendingAndReply_purgesPreviousAndReturnsMessage() throws Exception {
        User user = TestFixtures.user();
        var payload = new WhatsAppClarificationService.ClarificationPayload();
        payload.setAction("add");
        payload.setOriginalName("leche");
        payload.setQuantity(2);
        var line = new WhatsAppClarificationService.CandidateLine();
        line.setProductId(TestFixtures.PRODUCT_ID);
        line.setLabel("Leche entera");
        line.setScore(0.9);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        String reply = clarificationService.savePendingAndReply(user, payload, List.of(line));

        assertNotNull(reply);
        assertTrue(reply.contains("1)"));
        verify(pendingRepo).purgeForUser(user.getId());
        verify(pendingRepo).save(any(WhatsAppPendingClarification.class));
    }
}
