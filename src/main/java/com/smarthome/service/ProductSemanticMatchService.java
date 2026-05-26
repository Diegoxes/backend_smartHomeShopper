package com.smarthome.service;

import com.smarthome.entity.Product;
import com.smarthome.entity.ProductAlias;
import com.smarthome.repository.ProductAliasRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.util.TextNormalize;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Coincidencias por nombre oficial, alias registrados y similitud de cadena (Jaro–Winkler).
 * Resolución única sólo ante igualdad normalizada; el resto parecido devuelve candidatos ordenados para desambiguar por WhatsApp.
 */
@Service
@RequiredArgsConstructor
public class ProductSemanticMatchService {

    private final ProductRepository productRepo;
    private final ProductAliasRepository aliasRepo;

    private static final JaroWinklerSimilarity JW = new JaroWinklerSimilarity();
    /** Por debajo se trata como “producto nuevo” sin sugerencias. */
    static final double MIN_FUZZY_SUGGEST = 0.72;
    static final double MIN_TOP_TWO_GAP = 0.04;

    @Getter
    public static final class Candidate {
        private final Product product;
        private final double score;

        Candidate(Product product, double score) {
            this.product = product;
            this.score = score;
        }

        public String getLabel() {
            return product != null ? product.getName() : "";
        }

        public String getId() {
            return product != null ? product.getId() : "";
        }
    }

    public sealed interface MatchResult permits MatchExact, MatchFuzzy, MatchNone {}

    public record MatchExact(Product product) implements MatchResult {}

    public record MatchFuzzy(List<Candidate> candidates, String normalizedPhrase, String phraseRaw) implements MatchResult {}

    public record MatchNone(String normalizedPhrase, String phraseRaw) implements MatchResult {}

    /**
     * @param fuzzyIfNoExact Si {@code false} (varios ítems desde IA), sólo igualdad nominal/alias normalizada.
     */
    public MatchResult resolve(String orgId, String phraseRaw, boolean fuzzyIfNoExact) {
        String phrase = phraseRaw != null ? phraseRaw.trim() : "";
        String nPhrase = TextNormalize.forMatch(phrase);
        if (nPhrase.isEmpty()) {
            return new MatchNone("", phrase);
        }

        List<Product> products = productRepo.findByOrganizationId(orgId);
        List<ProductAlias> aliases = aliasRepo.findAllForOrganization(orgId);

        for (Product p : products) {
            if (nPhrase.equals(TextNormalize.forMatch(p.getName()))) {
                return new MatchExact(p);
            }
        }
        for (ProductAlias a : aliases) {
            if (nPhrase.equals(TextNormalize.forMatch(a.getNormalizedAlias()))) {
                return new MatchExact(a.getProduct());
            }
        }

        if (!fuzzyIfNoExact) {
            return new MatchNone(nPhrase, phrase);
        }

        Map<String, Double> bestByProductId = new HashMap<>();
        Map<String, Product> prodById = products.stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        for (Product p : products) {
            double s = JW.apply(TextNormalize.forMatch(p.getName()), nPhrase);
            mergeScore(bestByProductId, prodById, p.getId(), s);
        }
        for (ProductAlias a : aliases) {
            Product p = a.getProduct();
            if (p != null) {
                double s = JW.apply(TextNormalize.forMatch(a.getNormalizedAlias()), nPhrase);
                mergeScore(bestByProductId, prodById, p.getId(), s);
            }
        }

        List<Candidate> cand = bestByProductId.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_FUZZY_SUGGEST)
                .map(e -> new Candidate(prodById.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparingDouble(Candidate::getScore).reversed())
                .limit(6)
                .collect(Collectors.toList());

        if (!cand.isEmpty()) {
            return new MatchFuzzy(cand, nPhrase, phrase);
        }

        return new MatchNone(nPhrase, phrase);
    }

    public boolean isAmbiguousRanking(List<Candidate> sortedCandidates) {
        if (sortedCandidates == null || sortedCandidates.size() < 2) return false;
        double top = sortedCandidates.getFirst().getScore();
        double second = sortedCandidates.get(1).getScore();
        return top - second < MIN_TOP_TWO_GAP;
    }

    private static void mergeScore(Map<String, Double> bestByProductId, Map<String, Product> prodById, String pid, double s) {
        if (pid == null || !prodById.containsKey(pid)) return;
        bestByProductId.merge(pid, s, Double::max);
    }
}
