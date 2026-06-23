package com.smarthome.service;

import com.smarthome.entity.Product;
import com.smarthome.entity.ProductAlias;
import com.smarthome.repository.ProductAliasRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSemanticMatchServiceTest {

    @Mock ProductRepository productRepo;
    @Mock ProductAliasRepository aliasRepo;
    @InjectMocks ProductSemanticMatchService semanticMatchService;

    @Test
    void resolve_emptyPhrase_returnsMatchNone() {
        ProductSemanticMatchService.MatchResult result =
                semanticMatchService.resolve(TestFixtures.ORG_ID, "  ", true);
        assertInstanceOf(ProductSemanticMatchService.MatchNone.class, result);
    }

    @Test
    void resolve_exactNameMatch_returnsMatchExact() {
        Product product = TestFixtures.product();
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of(product));
        when(aliasRepo.findAllForOrganization(TestFixtures.ORG_ID)).thenReturn(List.of());

        ProductSemanticMatchService.MatchResult result =
                semanticMatchService.resolve(TestFixtures.ORG_ID, "Producto Test", true);

        assertInstanceOf(ProductSemanticMatchService.MatchExact.class, result);
        assertEquals(TestFixtures.PRODUCT_ID,
                ((ProductSemanticMatchService.MatchExact) result).product().getId());
    }

    @Test
    void resolve_aliasMatch_returnsMatchExact() {
        Product product = TestFixtures.product();
        ProductAlias alias = ProductAlias.builder()
                .product(product)
                .aliasText("alias test")
                .normalizedAlias("alias test")
                .build();
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of(product));
        when(aliasRepo.findAllForOrganization(TestFixtures.ORG_ID)).thenReturn(List.of(alias));

        ProductSemanticMatchService.MatchResult result =
                semanticMatchService.resolve(TestFixtures.ORG_ID, "alias test", true);

        assertInstanceOf(ProductSemanticMatchService.MatchExact.class, result);
    }

    @Test
    void resolve_partialNameContainedInProduct_returnsFuzzySingleCandidate() {
        Product inka = TestFixtures.product();
        inka.setName("Gaseosa Inka Kola");
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of(inka));
        when(aliasRepo.findAllForOrganization(TestFixtures.ORG_ID)).thenReturn(List.of());

        ProductSemanticMatchService.MatchResult result =
                semanticMatchService.resolve(TestFixtures.ORG_ID, "inka kola", true);

        assertInstanceOf(ProductSemanticMatchService.MatchFuzzy.class, result);
        var fuzzy = (ProductSemanticMatchService.MatchFuzzy) result;
        assertEquals(1, fuzzy.candidates().size());
        assertEquals("Gaseosa Inka Kola", fuzzy.candidates().get(0).getLabel());
    }

    @Test
    void isAmbiguousRanking_closeScores_returnsTrue() {
        Product p1 = TestFixtures.product();
        Product p2 = TestFixtures.product();
        p2.setId("prod-2");
        p2.setName("Producto Test 2");
        var c1 = new ProductSemanticMatchService.Candidate(p1, 0.80);
        var c2 = new ProductSemanticMatchService.Candidate(p2, 0.78);

        assertTrue(semanticMatchService.isAmbiguousRanking(List.of(c1, c2)));
    }
}
