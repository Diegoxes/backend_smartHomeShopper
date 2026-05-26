package com.smarthome.service;

import com.smarthome.entity.InventoryLot;
import com.smarthome.entity.Product;
import com.smarthome.entity.Warehouse;
import com.smarthome.repository.InventoryLotRepository;
import com.smarthome.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryLotService {

    private final InventoryLotRepository lotRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public void addLot(Product product, double quantity, LocalDate expiryDate) {
        if (product.getOrganization() == null) return;
        Warehouse wh = warehouseRepository.findDefaultByOrganizationId(product.getOrganization().getId())
                .orElse(null);
        if (wh == null) return;
        lotRepository.save(InventoryLot.builder()
                .product(product)
                .warehouse(wh)
                .quantity(quantity)
                .expiryDate(expiryDate)
                .build());
    }

    @Transactional
    public void consumeFifo(Product product, double amount) {
        if (product.getOrganization() == null || amount <= 0) return;
        List<InventoryLot> lots = lotRepository.findByProductIdOrderByReceivedAtAsc(product.getId());
        double remaining = amount;
        for (InventoryLot lot : lots) {
            if (remaining <= 0) break;
            double take = Math.min(lot.getQuantity(), remaining);
            lot.setQuantity(lot.getQuantity() - take);
            remaining -= take;
            if (lot.getQuantity() <= 0.0001) {
                lotRepository.delete(lot);
            } else {
                lotRepository.save(lot);
            }
        }
    }
}
