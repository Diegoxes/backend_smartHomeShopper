package com.smarthome.service;

import com.smarthome.entity.Product;
import com.smarthome.entity.ProductAlias;
import com.smarthome.repository.ProductAliasRepository;
import com.smarthome.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductAliasService {

    private final ProductAliasRepository aliasRepo;
    private final ProductRepository productRepo;
    private final OrganizationContextService orgContext;

    @Transactional(readOnly = true)
    public void assertProductOwned(String productId) {
        productRepo.findByIdAndOrganizationId(productId, orgContext.requireActiveOrgId())
                .orElseThrow(() -> new RuntimeException("Forbidden"));
    }

    @Transactional
    public com.smarthome.dto.Dto.ProductAliasDto addManual(String productId, String userId, String aliasText) {
        assertProductOwned(productId);
        Product p = productRepo.findById(productId).orElseThrow();
        String trimmed = aliasText != null ? aliasText.trim() : "";
        if (trimmed.length() < 2) throw new IllegalArgumentException("alias demasiado corto");
        String norm = com.smarthome.util.TextNormalize.forMatch(trimmed);
        if (norm.isBlank()) throw new IllegalArgumentException("alias inválido");
        if (aliasRepo.existsByProductIdAndNormalizedAlias(productId, norm)) {
            throw new IllegalStateException("Ese alias ya existe para este producto");
        }
        ProductAlias a = ProductAlias.builder()
                .product(p)
                .aliasText(trimmed)
                .normalizedAlias(norm)
                .learnedWhatsApp(false)
                .build();
        aliasRepo.save(a);
        return toDto(a);
    }

    @Transactional
    public void recordLearnedSynonym(String productId, String orgId, String spokenPhrase) {
        if (spokenPhrase == null || spokenPhrase.isBlank()) return;
        Product p = productRepo.findByIdAndOrganizationId(productId, orgId).orElse(null);
        if (p == null) return;
        String normPhrase = com.smarthome.util.TextNormalize.forMatch(spokenPhrase);
        String canon = com.smarthome.util.TextNormalize.forMatch(p.getName());
        if (normPhrase.isBlank() || normPhrase.equals(canon)) return;
        if (aliasRepo.existsByProductIdAndNormalizedAlias(productId, normPhrase)) return;
        aliasRepo.save(ProductAlias.builder()
                .product(p)
                .aliasText(spokenPhrase.trim())
                .normalizedAlias(normPhrase)
                .learnedWhatsApp(true)
                .build());
    }

    static com.smarthome.dto.Dto.ProductAliasDto toDto(ProductAlias a) {
        return com.smarthome.dto.Dto.ProductAliasDto.builder()
                .id(a.getId())
                .aliasText(a.getAliasText())
                .normalizedAlias(a.getNormalizedAlias())
                .learnedWhatsApp(a.isLearnedWhatsApp())
                .build();
    }
}
