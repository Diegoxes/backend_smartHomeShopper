package com.smarthome.service;

import com.smarthome.entity.OrganizationMember;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final UserRepository userRepo;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationSettingsRepository settingsRepository;
    private final ProductRepository productRepo;
    private final AiService aiService;
    private final WhatsAppClarificationService clarification;
    private final ProductSemanticMatchService semanticMatch;
    private final WhatsAppInventoryActionService actions;

    @Value("${app.features.ai.enabled:false}")
    private boolean aiEnabled;

    @Transactional
    public String handleIncoming(String fromRaw, String body) {
        String phone = fromRaw.replace("whatsapp:", "").trim();
        Optional<OrganizationMember> memberOpt = memberRepository.findByUserWhatsappNumber(phone);

        if (memberOpt.isEmpty()) {
            return "Hola. No encontramos tu número en ningún equipo. Pide a tu administrador que lo registre en Inventario B2B.";
        }

        OrganizationMember member = memberOpt.get();
        User user = member.getUser();
        String orgId = member.getOrganization().getId();

        Optional<String> clarified = clarification.consumeReplyIfPending(user, body);
        if (clarified.isPresent()) {
            return clarified.get();
        }

        String normalized = body == null ? "" : body.trim().toLowerCase(Locale.ROOT);

        if (normalized.equals("inventario") || normalized.equals("stock") || normalized.equals("lista")) {
            return buildInventoryMessage(orgId);
        }
        if (normalized.equals("alertas") || normalized.equals("bajos")) {
            return buildAlertsMessage(orgId);
        }
        if (normalized.equals("ayuda") || normalized.equals("help")) {
            return helpMessage();
        }
        if (body != null && body.trim().startsWith("-")) {
            return handleQuickConsume(user, orgId, body.trim());
        }

        if (!aiEnabled) {
            return helpMessage();
        }
        return aiService.parseWhatsAppMessage(user, orgId, body != null ? body : "");
    }

    private String helpMessage() {
        return """
                *Comandos disponibles*
                • *inventario* — ver stock
                • *alertas* — productos bajos o por vencer
                • *-[producto]* — consumir 1 unidad (ej: *-leche*)
                • Responde con el número si te pedimos desambiguar
                """;
    }

    private String buildInventoryMessage(String orgId) {
        List<Product> products = productRepo.findByOrganizationId(orgId);
        if (products.isEmpty()) return "Tu catálogo está vacío. Agrega productos desde la aplicación web.";

        StringBuilder sb = new StringBuilder("*Inventario*\n\n");
        for (Product p : products) {
            String icon = p.isLowStock() ? "⚠️" : "✅";
            sb.append(String.format("%s *%s*: %.1f %s\n",
                    icon, p.getName(), p.getQuantity(), p.getUnit().name().toLowerCase(Locale.ROOT)));
        }
        sb.append("\nEnvía *-[producto]* para restar 1 unidad.");
        return sb.toString();
    }

    private String buildAlertsMessage(String orgId) {
        int alertDays = settingsRepository.findByOrganizationId(orgId)
                .map(s -> s.getExpiryAlertDays()).orElse(7);
        List<Product> low = productRepo.findLowStockByOrganizationId(orgId);
        List<Product> expiring = productRepo.findExpiringByOrganizationId(orgId,
                java.time.LocalDate.now().plusDays(alertDays));

        if (low.isEmpty() && expiring.isEmpty())
            return "✅ Sin alertas activas en tu inventario.";

        StringBuilder sb = new StringBuilder("*Alertas*\n\n");
        if (!low.isEmpty()) {
            sb.append("*Stock bajo:*\n");
            low.forEach(p -> sb.append(String.format("• %s (%.1f %s)\n",
                    p.getName(), p.getQuantity(), p.getUnit().name().toLowerCase(Locale.ROOT))));
        }
        if (!expiring.isEmpty()) {
            sb.append("\n*Por vencer:*\n");
            expiring.forEach(p -> sb.append(String.format("• %s (vence: %s)\n", p.getName(), p.getExpiryDate())));
        }
        return sb.toString();
    }

    private String handleQuickConsume(User user, String orgId, String rawLine) {
        String inner = rawLine.startsWith("-") ? rawLine.substring(1).trim() : rawLine.trim();
        if (inner.isEmpty()) {
            return "Indica producto después del guión, ej.: *-leche*.";
        }

        String[] parts = inner.split("\\s+", 2);
        String spoken = parts[0].trim();
        double amount = 1.0;
        if (parts.length > 1) {
            amount = safeDouble(parts[1].trim().split("\\s+")[0]);
        }

        ProductSemanticMatchService.MatchResult res = semanticMatch.resolve(orgId, spoken, true);

        if (res instanceof ProductSemanticMatchService.MatchExact ex) {
            Product p = ex.product();
            actions.consume(user, p, amount);
            Product refreshed = productRepo.findById(p.getId()).orElse(p);
            double newQty = refreshed.getQuantity();
            String alert = refreshed.isLowStock() ? "\n⚠️ Stock bajo." : "";
            return String.format("✅ *%s*: %.1f %s restante%s",
                    refreshed.getName(), newQty, refreshed.getUnit().name().toLowerCase(Locale.ROOT), alert);
        }

        if (res instanceof ProductSemanticMatchService.MatchFuzzy mf && mf.candidates() != null && !mf.candidates().isEmpty()) {
            var lines = mf.candidates().stream().limit(5).map(c -> {
                var line = new WhatsAppClarificationService.CandidateLine();
                line.setProductId(c.getId());
                line.setLabel(c.getLabel());
                line.setScore(c.getScore());
                return line;
            }).collect(Collectors.toList());

            var payload = new WhatsAppClarificationService.ClarificationPayload();
            payload.setKind("quick_consume");
            payload.setAction("consume");
            payload.setOriginalName(spoken);
            payload.setQuantity(amount);
            payload.setUnit("UNIT");
            return clarification.savePendingAndReply(user, payload, lines);
        }

        return "No encontré *" + spoken + "* en tu catálogo.";
    }

    private double safeDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 1.0; }
    }
}
