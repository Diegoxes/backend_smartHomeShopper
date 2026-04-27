package com.smarthome.service;

import com.smarthome.entity.ConsumptionLog;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.ConsumptionLogRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final ConsumptionLogRepository logRepo;
    private final AiService aiService;

    @Value("${twilio.whatsapp-from}")
    private String twilioFrom;

    @Transactional
    public String handleIncoming(String fromRaw, String body) {
        String phone = fromRaw.replace("whatsapp:", "").trim();
        Optional<User> userOpt = userRepo.findByWhatsappNumber(phone);

        if (userOpt.isEmpty()) {
            return "Hola! No encontré tu cuenta. Regístrate en smarthome.app con este número de WhatsApp.";
        }

        User user = userOpt.get();
        String msg = body.trim().toLowerCase();

        if (msg.equals("inventario") || msg.equals("stock") || msg.equals("lista")) {
            return buildInventoryMessage(user.getId());
        }
        if (msg.equals("alertas") || msg.equals("bajos")) {
            return buildAlertsMessage(user.getId());
        }
        if (msg.startsWith("-")) {
            return handleQuickConsume(user, msg);
        }

        return aiService.parseWhatsAppMessage(user, body);
    }

    private String buildInventoryMessage(String userId) {
        List<Product> products = productRepo.findByUserId(userId);
        if (products.isEmpty()) return "Tu inventario está vacío. Agrega productos desde la app.";

        StringBuilder sb = new StringBuilder("*Tu inventario* 🏠\n\n");
        for (Product p : products) {
            String icon = p.isLowStock() ? "⚠️" : "✅";
            sb.append(String.format("%s *%s*: %.1f %s\n", icon, p.getName(), p.getQuantity(), p.getUnit().name().toLowerCase()));
        }
        sb.append("\nEnvía *-[producto]* para restar 1 unidad. Ej: *-leche*");
        return sb.toString();
    }

    private String buildAlertsMessage(String userId) {
        List<Product> low      = productRepo.findLowStockByUserId(userId);
        List<Product> expiring = productRepo.findExpiringByUserId(userId, java.time.LocalDate.now().plusDays(7));

        if (low.isEmpty() && expiring.isEmpty())
            return "✅ Todo bien! No hay alertas en tu inventario.";

        StringBuilder sb = new StringBuilder("*Alertas de inventario* ⚠️\n\n");
        if (!low.isEmpty()) {
            sb.append("*Stock bajo:*\n");
            low.forEach(p -> sb.append(String.format("• %s (%.1f %s)\n", p.getName(), p.getQuantity(), p.getUnit().name().toLowerCase())));
        }
        if (!expiring.isEmpty()) {
            sb.append("\n*Por vencer:*\n");
            expiring.forEach(p -> sb.append(String.format("• %s (vence: %s)\n", p.getName(), p.getExpiryDate())));
        }
        return sb.toString();
    }

    private String handleQuickConsume(User user, String msg) {
        String[] parts = msg.substring(1).trim().split("\\s+");
        String productName = parts[0];
        double amount = parts.length > 1 ? safeDouble(parts[1]) : 1.0;

        List<Product> products = productRepo.findByUserId(user.getId());
        Optional<Product> match = products.stream()
                .filter(p -> p.getName().toLowerCase().contains(productName))
                .findFirst();

        if (match.isEmpty())
            return "No encontré *" + productName + "* en tu inventario.";

        Product p = match.get();
        double newQty = Math.max(0, p.getQuantity() - amount);
        p.setQuantity(newQty);
        productRepo.save(p);

        logRepo.save(ConsumptionLog.builder()
                .product(p).quantityChange(-amount)
                .actionType(ConsumptionLog.ActionType.CONSUMED)
                .source(ConsumptionLog.Source.WHATSAPP)
                .build());

        String alert = p.isLowStock() ? "\n⚠️ Stock bajo! Considera reponerlo pronto." : "";
        return String.format("✅ *%s* actualizado: %.1f %s restante%s", p.getName(), newQty, p.getUnit().name().toLowerCase(), alert);
    }

    private double safeDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 1.0; }
    }
}
