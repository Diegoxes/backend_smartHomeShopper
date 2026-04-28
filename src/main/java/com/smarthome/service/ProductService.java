package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.ConsumptionLog;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.ConsumptionLogRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepo;
    private final ConsumptionLogRepository logRepo;
    private final UserRepository userRepo;

    public List<Dto.ProductResponse> getAllByUser(String userId) {
        return productRepo.findByUserId(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Dto.ProductResponse getById(String productId, String userId) {
        Product p = findOwned(productId, userId);
        return toResponse(p);
    }

    @Transactional
    public Dto.ProductResponse create(String userId, Dto.CreateProductRequest req) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Product p = Product.builder()
                .user(user)
                .name(req.getName())
                .quantity(req.getQuantity())
                .minQuantity(req.getMinQuantity())
                .unit(req.getUnit())
                .consumptionPerUse(req.getConsumptionPerUse() != null ? req.getConsumptionPerUse() : 1.0)
                .expiryDate(req.getExpiryDate())
                .barcode(req.getBarcode())
                .category(req.getCategory())
                .imageUrl(req.getImageUrl())
                .build();
        return toResponse(productRepo.save(p));
    }

    @Transactional
    public Dto.ProductResponse update(String productId, String userId, Dto.UpdateProductRequest req) {
        Product p = findOwned(productId, userId);
        if (req.getName() != null)             p.setName(req.getName());
        if (req.getQuantity() != null)         p.setQuantity(req.getQuantity());
        if (req.getMinQuantity() != null)      p.setMinQuantity(req.getMinQuantity());
        if (req.getUnit() != null)             p.setUnit(req.getUnit());
        if (req.getConsumptionPerUse() != null) p.setConsumptionPerUse(req.getConsumptionPerUse());
        if (req.getExpiryDate() != null)       p.setExpiryDate(req.getExpiryDate());
        if (req.getCategory() != null)         p.setCategory(req.getCategory());
        return toResponse(productRepo.save(p));
    }

    @Transactional
    public Dto.ProductResponse consume(String productId, String userId, Dto.ConsumeRequest req) {
        Product p = findOwned(productId, userId);
        double newQty = Math.max(0, p.getQuantity() - req.getAmount());
        p.setQuantity(newQty);
        productRepo.save(p);

        logRepo.save(ConsumptionLog.builder()
                .product(p)
                .quantityChange(-req.getAmount())
                .actionType(ConsumptionLog.ActionType.CONSUMED)
                .source(ConsumptionLog.Source.WEB)
                .note(req.getNote())
                .build());

        return toResponse(p);
    }

    @Transactional
    public Dto.ProductResponse restock(String productId, String userId, Dto.ConsumeRequest req) {
        Product p = findOwned(productId, userId);
        p.setQuantity(p.getQuantity() + req.getAmount());
        productRepo.save(p);

        logRepo.save(ConsumptionLog.builder()
                .product(p)
                .quantityChange(req.getAmount())
                .actionType(ConsumptionLog.ActionType.RESTOCKED)
                .source(ConsumptionLog.Source.WEB)
                .note(req.getNote())
                .build());

        return toResponse(p);
    }

    @Transactional
    public void delete(String productId, String userId) {
        Product p = findOwned(productId, userId);
        productRepo.delete(p);
    }

    public Dto.DashboardResponse getDashboard(String userId) {
        List<Dto.ProductResponse> all        = getAllByUser(userId);
        List<Dto.ProductResponse> lowStock   = all.stream().filter(Dto.ProductResponse::isLowStock).collect(Collectors.toList());
        List<Dto.ProductResponse> expiring   = all.stream().filter(Dto.ProductResponse::isExpiringSoon).collect(Collectors.toList());

        return Dto.DashboardResponse.builder()
                .totalProducts(all.size())
                .lowStockCount(lowStock.size())
                .expiringCount(expiring.size())
                .lowStockProducts(lowStock)
                .expiringProducts(expiring)
                .allProducts(all)
                .build();
    }

    private Product findOwned(String productId, String userId) {
        Product p = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        if (!p.getUser().getId().equals(userId))
            throw new RuntimeException("Forbidden");
        return p;
    }

    private Double predictDaysUntilEmpty(Product p) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        Double avg = logRepo.avgDailyConsumption(p.getId(), since, 30, ConsumptionLog.ActionType.CONSUMED);
        if (avg == null || avg == 0) return null;
        return p.getQuantity() / avg;
    }

    public Dto.ProductResponse toResponse(Product p) {
        return Dto.ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .quantity(p.getQuantity())
                .minQuantity(p.getMinQuantity())
                .unit(p.getUnit().name())
                .consumptionPerUse(p.getConsumptionPerUse())
                .expiryDate(p.getExpiryDate())
                .barcode(p.getBarcode())
                .category(p.getCategory())
                .imageUrl(p.getImageUrl())
                .lowStock(p.isLowStock())
                .expiringSoon(p.isExpiringSoon())
                .daysUntilEmpty(predictDaysUntilEmpty(p))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
