package com.smarthome.service;

import com.smarthome.dto.WhatsAppReply;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private static final Pattern SIGNED_QTY = Pattern.compile("^([+-])(\\d+(?:\\.\\d+)?)\\s+(.+)$");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationSettingsRepository settingsRepository;
    private final ProductRepository productRepo;
    private final AiService aiService;
    private final WhatsAppClarificationService clarification;
    private final ProductSemanticMatchService semanticMatch;
    private final WhatsAppInventoryActionService actions;
    private final ReportExportService reportExportService;
    private final WhatsAppReportTokenService reportTokenService;

    @Value("${app.features.ai.enabled:false}")
    private boolean aiEnabled;

    @Transactional
    public WhatsAppReply handleIncoming(String fromRaw, String body) {
        String phone = fromRaw.replace("whatsapp:", "").trim();
        Optional<OrganizationMember> memberOpt = memberRepository.findByUserWhatsappNumber(phone);

        if (memberOpt.isEmpty()) {
            return WhatsAppReply.textOnly(
                    "Hola. No encontramos tu número en ningún equipo. Pide a tu administrador que lo registre en Inventario B2B.");
        }

        OrganizationMember member = memberOpt.get();
        User user = member.getUser();
        String orgId = member.getOrganization().getId();

        Optional<String> clarified = clarification.consumeReplyIfPending(user, body);
        if (clarified.isPresent()) {
            return WhatsAppReply.textOnly(clarified.get());
        }

        String normalized = body == null ? "" : body.trim().toLowerCase(Locale.ROOT);

        if (normalized.equals("inventario") || normalized.equals("stock") || normalized.equals("lista")) {
            return WhatsAppReply.textOnly(buildInventoryMessage(orgId));
        }
        if (normalized.equals("alertas") || normalized.equals("bajos")) {
            return WhatsAppReply.textOnly(buildAlertsMessage(orgId));
        }
        if (normalized.equals("ayuda") || normalized.equals("help")) {
            return WhatsAppReply.textOnly(helpMessage());
        }
        if (normalized.equals("reporte") || normalized.startsWith("reporte ")) {
            return handleReportCommand(orgId, normalized);
        }

        if (body != null) {
            String trimmed = body.trim();
            Matcher signedMatcher = SIGNED_QTY.matcher(trimmed);
            if (signedMatcher.matches()) {
                boolean isAdd = "+".equals(signedMatcher.group(1));
                double amount = Double.parseDouble(signedMatcher.group(2));
                String productName = signedMatcher.group(3).trim();
                return WhatsAppReply.textOnly(handleSignedAdjust(user, orgId, isAdd, amount, productName));
            }
            if (trimmed.startsWith("-")) {
                return WhatsAppReply.textOnly(handleQuickConsume(user, orgId, trimmed));
            }
        }

        if (!aiEnabled) {
            return WhatsAppReply.textOnly(helpMessage());
        }
        return WhatsAppReply.textOnly(aiService.parseWhatsAppMessage(user, orgId, body != null ? body : ""));
    }

    private String helpMessage() {
        return """
                *Comandos disponibles*
                • *inventario* — ver stock
                • *alertas* — productos bajos o por vencer
                • *-[producto]* — consumir 1 unidad (ej: *-leche*)
                • *-5 leche* / *+10 leche* — restar o sumar cantidad
                • *reporte* — ver tipos de reporte Excel
                • *reporte inventario* | *rotacion* | *completo*
                • Responde con el número si te pedimos desambiguar
                """;
    }

    private String reportHelpMessage() {
        return """
                *Reportes Excel*
                Envía uno de estos comandos para recibir un archivo:

                • *reporte inventario* — resumen + detalle por producto
                • *reporte rotacion* — consumo últimos 30 días
                • *reporte completo* — inventario + rotación
                """;
    }

    private WhatsAppReply handleReportCommand(String orgId, String normalized) {
        String typePart = normalized.equals("reporte") ? "" : normalized.substring("reporte".length()).trim();
        if (typePart.isEmpty()) {
            return WhatsAppReply.textOnly(reportHelpMessage());
        }

        if (!reportTokenService.isPublicBaseUrlConfigured()) {
            return WhatsAppReply.textOnly(
                    "Los reportes por WhatsApp no están configurados en el servidor. Contacta al administrador.");
        }

        LocalDate today = LocalDate.now();
        String dateSuffix = today.format(FILE_DATE);
        byte[] data;
        String fileName;
        String label;

        switch (typePart) {
            case "inventario", "stock" -> {
                data = reportExportService.exportInventarioXlsxForOrg(orgId);
                fileName = "reporte-inventario-" + dateSuffix + ".xlsx";
                label = "inventario";
            }
            case "rotacion", "rotación" -> {
                data = reportExportService.exportRotacionXlsxForOrg(orgId, null, null);
                fileName = "reporte-rotacion-" + dateSuffix + ".xlsx";
                label = "rotación";
            }
            case "completo", "general" -> {
                data = reportExportService.exportCompletoXlsxForOrg(orgId, null, null);
                fileName = "reporte-completo-" + dateSuffix + ".xlsx";
                label = "completo";
            }
            default -> {
                return WhatsAppReply.textOnly(
                        "Tipo de reporte no reconocido: *" + typePart + "*.\n\n" + reportHelpMessage());
            }
        }

        return reportTokenService.store(orgId, fileName, data)
                .map(stored -> WhatsAppReply.withMedia(
                        "📊 Aquí tienes tu reporte de *" + label + "* (" + today + ").", stored.mediaUrl()))
                .orElse(WhatsAppReply.textOnly(
                        "No pude preparar el archivo. Intenta de nuevo en un momento."));
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
        sb.append("\nEnvía *-[producto]* para restar 1 unidad o *+10 producto* para sumar.");
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

    private String handleSignedAdjust(User user, String orgId, boolean isAdd, double amount, String spoken) {
        if (amount <= 0) {
            return "La cantidad debe ser mayor que cero.";
        }

        ProductSemanticMatchService.MatchResult res = semanticMatch.resolve(orgId, spoken, true);
        String action = isAdd ? "add" : "consume";

        if (res instanceof ProductSemanticMatchService.MatchExact ex) {
            Product p = ex.product();
            applyAdjust(user, p, isAdd, amount);
            Product refreshed = productRepo.findById(p.getId()).orElse(p);
            return formatAdjustReply(refreshed, isAdd);
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
            payload.setKind("quick_adjust");
            payload.setAction(action);
            payload.setOriginalName(spoken);
            payload.setQuantity(amount);
            payload.setUnit("UNIT");
            return clarification.savePendingAndReply(user, payload, lines);
        }

        return "No encontré *" + spoken + "* en tu catálogo.";
    }

    private void applyAdjust(User user, Product p, boolean isAdd, double amount) {
        if (isAdd) {
            actions.addOrRestock(user, p.getUnit(), amount, p.getName(), p);
        } else {
            actions.consume(user, p, amount);
        }
    }

    private String formatAdjustReply(Product refreshed, boolean isAdd) {
        double newQty = refreshed.getQuantity();
        String unit = refreshed.getUnit().name().toLowerCase(Locale.ROOT);
        String alert = refreshed.isLowStock() ? "\n⚠️ Stock bajo." : "";
        if (isAdd) {
            return String.format("✅ *%s* actualizado. Stock: %.1f %s.%s",
                    refreshed.getName(), newQty, unit, alert);
        }
        return String.format("✅ *%s*: %.1f %s restante%s",
                refreshed.getName(), newQty, unit, alert);
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
            return formatAdjustReply(refreshed, false);
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
