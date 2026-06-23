package com.smarthome.service;

import com.smarthome.entity.ConsumptionLog;
import com.smarthome.entity.Organization;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.ConsumptionLogRepository;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppInventoryActionService {

    private final ProductRepository productRepo;
    private final ConsumptionLogRepository logRepo;
    private final OrganizationMemberRepository memberRepository;
    private final InventoryLotService inventoryLotService;
    private final ProductUomService productUomService;
    private final OrganizationContextService orgContext;

    @Transactional
    public void consume(User user, Product p, double quantity) {
        consume(user, p, quantity, null);
    }

    @Transactional
    public void consume(User user, Product p, double quantity, String measureUnitId) {
        String orgId = p.getOrganization() != null ? p.getOrganization().getId() : orgContext.requireActiveOrgId();
        double stockUnits = measureUnitId != null && !measureUnitId.isBlank()
                ? productUomService.toBaseUnits(p.getId(), measureUnitId, quantity)
                : quantity;
        inventoryLotService.consumeFifo(p, stockUnits);
        double newQty = Math.max(0, p.getQuantity() - stockUnits);
        p.setQuantity(newQty);
        productRepo.save(p);
        logRepo.save(ConsumptionLog.builder()
                .product(p).quantityChange(-stockUnits)
                .actionType(ConsumptionLog.ActionType.CONSUMED)
                .source(ConsumptionLog.Source.WHATSAPP)
                .inputQuantity(quantity)
                .build());
    }

    @Transactional
    public void addOrRestock(User user, Product.UnitType unit, double quantity,
                             String canonicalNameWhenNew,
                             Product existingOrNull) {
        addOrRestock(user, unit, quantity, canonicalNameWhenNew, existingOrNull, null);
    }

    @Transactional
    public void addOrRestock(User user, Product.UnitType unit, double quantity,
                             String canonicalNameWhenNew,
                             Product existingOrNull, String measureUnitId) {
        if (existingOrNull != null) {
            Product p = existingOrNull;
            String orgId = p.getOrganization().getId();
            double stockUnits = measureUnitId != null && !measureUnitId.isBlank()
                    ? productUomService.toBaseUnits(p.getId(), measureUnitId, quantity)
                    : quantity;
            p.setQuantity(p.getQuantity() + stockUnits);
            productRepo.save(p);
            inventoryLotService.addLot(p, stockUnits, p.getExpiryDate());
            logRepo.save(ConsumptionLog.builder()
                    .product(p)
                    .quantityChange(stockUnits)
                    .actionType(ConsumptionLog.ActionType.RESTOCKED)
                    .source(ConsumptionLog.Source.WHATSAPP)
                    .inputQuantity(quantity)
                    .build());
            return;
        }

        OrganizationMember member = memberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Usuario sin organización"));
        Organization org = member.getOrganization();

        Product p = Product.builder()
                .organization(org)
                .user(user)
                .sku("WA-" + System.currentTimeMillis())
                .name(canonicalNameWhenNew != null ? canonicalNameWhenNew : "Ítem sin nombre")
                .quantity(quantity)
                .minQuantity(1.0)
                .unit(unit != null ? unit : Product.UnitType.UNIT)
                .consumptionPerUse(1.0)
                .build();
        productRepo.save(p);
        inventoryLotService.addLot(p, quantity, null);
        logRepo.save(ConsumptionLog.builder()
                .product(p)
                .quantityChange(quantity)
                .actionType(ConsumptionLog.ActionType.RESTOCKED)
                .source(ConsumptionLog.Source.WHATSAPP)
                .inputQuantity(quantity)
                .build());
    }
}
