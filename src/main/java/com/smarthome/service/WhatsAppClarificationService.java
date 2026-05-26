package com.smarthome.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.entity.WhatsAppPendingClarification;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.WhatsAppPendingClarificationRepository;
import com.smarthome.util.TextNormalize;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppClarificationService {

    private static final Pattern LEADING_INT = Pattern.compile("^\\s*(\\d+)\\s*$");
    private static final Pattern CREAR = Pattern.compile("(?i).*\\b(crear|nuevo|nueva)\\b.*");

    private final WhatsAppPendingClarificationRepository pendingRepo;
    private final ProductRepository productRepo;
    private final OrganizationMemberRepository memberRepository;
    private final WhatsAppInventoryActionService actions;
    private final ProductAliasService productAliasService;
    private final ObjectMapper objectMapper;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClarificationPayload {
        private String kind;
        private String action;
        private String originalName;
        private double quantity;
        private String unit;
        private List<CandidateLine> candidates;
    }

    @Data
    public static class CandidateLine {
        private String productId;
        private String label;
        private double score;
    }

    /** Devuelve el mensaje Twilio si procesó respuesta pendiente; vacío = seguir pipeline normal. */
    @Transactional
    public Optional<String> consumeReplyIfPending(User user, String messageBody) {
        if (user == null || messageBody == null) return Optional.empty();
        Optional<WhatsAppPendingClarification> pend = pendingRepo.findActiveForUser(user.getId(), LocalDateTime.now());
        if (pend.isEmpty()) return Optional.empty();

        WhatsAppPendingClarification row = pend.get();
        ClarificationPayload payload;
        try {
            payload = objectMapper.readValue(row.getPayloadJson(), ClarificationPayload.class);
        } catch (Exception e) {
            log.warn("Payload de clarificación ilegible, se descarta.", e);
            pendingRepo.delete(row);
            return Optional.of("Hubo un error técnico. Repite tu pedido anterior, por favor.");
        }

        String msg = messageBody.trim();
        if (payload.getCandidates() == null || payload.getCandidates().isEmpty()) {
            pendingRepo.delete(row);
            return Optional.of("No hay opciones pendientes. Envía otro mensaje.");
        }

        int createChoice = payload.getCandidates().size() + 1;

        if (CREAR.matcher(msg).matches()) {
            return finalizeCreateNew(user, row, payload);
        }

        Matcher mNum = LEADING_INT.matcher(msg);
        if (mNum.matches()) {
            int idx = Integer.parseInt(mNum.group(1));
            if (idx == createChoice) {
                return finalizeCreateNew(user, row, payload);
            }
            if (idx >= 1 && idx <= payload.getCandidates().size()) {
                CandidateLine c = payload.getCandidates().get(idx - 1);
                return finalizeExisting(user, row, payload, c.getProductId());
            }
        }

        String n = TextNormalize.forMatch(msg);
        for (int i = 0; i < payload.getCandidates().size(); i++) {
            CandidateLine c = payload.getCandidates().get(i);
            if (TextNormalize.forMatch(c.getLabel()).equals(n)) {
                return finalizeExisting(user, row, payload, c.getProductId());
            }
        }

        int optCreate = payload.getCandidates().size() + 1;
        return Optional.of("""
                No entendí la opción.
                Responde con el *número* (1–%d) o escribe *crear* si quieres el producto *%s*."""
                .formatted(optCreate,
                        escapeXml(payload.getOriginalName() != null ? payload.getOriginalName() : "nuevo")));

    }

    @Transactional
    public void removePending(User user) {
        if (user != null) {
            pendingRepo.purgeForUser(user.getId());
        }
    }

    @Transactional
    public String savePendingAndReply(User user, ClarificationPayload payload, List<CandidateLine> trimmedCandidates) {
        payload.setCandidates(trimmedCandidates);
        try {
            String json = objectMapper.writeValueAsString(payload);
            pendingRepo.purgeForUser(user.getId());

            WhatsAppPendingClarification row = WhatsAppPendingClarification.builder()
                    .user(user)
                    .payloadJson(json)
                    .expiresAt(LocalDateTime.now().plusMinutes(20))
                    .build();
            pendingRepo.save(row);
        } catch (Exception e) {
            log.error("No se pudo guardar aclaración pendiente", e);
            return "Error interno guardando opciones; intenta en un momento.";
        }
        int nOpt = trimmedCandidates.size() + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("🤔 *¿Te referías a alguno de estos?*\n");
        sb.append("(dijiste algo parecido a *").append(escapeXml(payload.getOriginalName())).append("*)\n\n");
        for (int i = 0; i < trimmedCandidates.size(); i++) {
            CandidateLine c = trimmedCandidates.get(i);
            sb.append(i + 1).append(") *").append(escapeXml(c.getLabel())).append("*\n");
        }
        sb.append(nOpt).append(") Crear *nuevo producto*: ").append(escapeXml(payload.getOriginalName())).append("\n\n")
                .append("Responde con el *número* o escribe *crear*.");
        return sb.toString();
    }

    private Optional<String> finalizeExisting(User user, WhatsAppPendingClarification row, ClarificationPayload p, String productId) {
        var member = memberRepository.findByUserId(user.getId()).orElse(null);
        String orgId = member != null ? member.getOrganization().getId() : null;
        Product prod = orgId != null
                ? productRepo.findByIdAndOrganizationId(productId, orgId).orElse(null)
                : null;
        if (prod == null) {
            pendingRepo.delete(row);
            return Optional.of("Ese ítem ya no existe; envía otro comando.");
        }
        try {
            if (orgId != null) {
                productAliasService.recordLearnedSynonym(productId, orgId, p.getOriginalName());
            }
        } catch (Exception ex) {
            log.debug("No se registró alias (duplicado o vacío): {}", ex.getMessage());
        }

        Product.UnitType ut = WhatsAppAiSupport.safeUnit(p.getUnit());
        if ("consume".equalsIgnoreCase(p.getAction())) {
            actions.consume(user, prod, p.getQuantity());
        } else if ("add".equalsIgnoreCase(p.getAction())) {
            actions.addOrRestock(user, ut, p.getQuantity(), prod.getName(), prod);
        } else {
            pendingRepo.delete(row);
            return Optional.of("Acción desconocida; repite desde cero.");
        }

        Product fresh = productRepo.findById(productId).orElse(prod);
        pendingRepo.delete(row);
        return Optional.of(replyAfterMutation(p.getAction(), fresh, ut));
    }

    private Optional<String> finalizeCreateNew(User user, WhatsAppPendingClarification row, ClarificationPayload p) {
        Product.UnitType ut = WhatsAppAiSupport.safeUnit(p.getUnit());
        String nm = p.getOriginalName() != null ? p.getOriginalName().trim() : "Ítem";
        actions.addOrRestock(user, ut, p.getQuantity(), nm, null);
        pendingRepo.delete(row);
        return Optional.of(("✅ Listo: registré como *producto nuevo* *"
                + escapeXml(nm)
                + "* con cantidad *" + p.getQuantity() + "*."));
    }

    private static String replyAfterMutation(String action, Product prod, Product.UnitType unit) {
        String u = unit != null ? unit.name().toLowerCase() : "unidad(es)";
        if ("consume".equalsIgnoreCase(action)) {
            double q = prod.getQuantity();
            String alert = prod.isLowStock() ? "\n⚠️ Quedaste con stock *bajo*." : "";
            return "✅ *" + escapeXml(prod.getName()) + "*: ahora tienes *" + q + "* " + u + "." + alert;
        }
        return "✅ *" + escapeXml(prod.getName()) + "* actualizado. Stock: *"
                + prod.getQuantity() + "* " + u + ".";
    }

    /** Escapes mínimos para Twilio body inside *bold* — evita cortar marcado. */
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "")
                .replace("<", "").replace(">", "");
    }
}
