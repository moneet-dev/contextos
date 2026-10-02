package dev.moneet.contextos.core.context;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContextPackagerTest {

    private final ContextPackageRenderer renderer = new ContextPackageRenderer();
    private final TokenEstimator estimator = TokenEstimator.defaultEstimator();
    private final ContextPackager packager = new ContextPackager(estimator, renderer);

    @Test
    void shouldIncludeEverythingWithUnlimitedBudget() {
        ContextPackage pkg = packager.pack(ContextRequest.of("t", 1),
                List.of(item("a", "code", 0.5, 100), item("b", "sql", 0.9, 100)));

        assertEquals(List.of("b", "a"), ids(pkg));
        assertTrue(pkg.omitted().isEmpty());
        assertEquals(estimator.estimate(pkg.rendered()), pkg.usedTokens());
    }

    @Test
    void shouldPackGreedilyByScoreAndKeepSmallerItemsThatStillFit() {
        ContextItem big = item("big", "code", 0.9, 2000);
        ContextItem small = item("small", "code", 0.5, 40);
        ContextItem top = item("top", "code", 1.0, 200);
        int budget = cost(top, true) + cost(small, false) + 5;

        ContextPackage pkg = packager.pack(request(budget), List.of(big, small, top));

        assertEquals(List.of("top", "small"), ids(pkg));
        assertEquals(List.of("big"), pkg.omitted().stream().map(e -> e.item().id()).toList());
        assertTrue(pkg.usedTokens() <= budget);
    }

    @Test
    void shouldChargeDomainHeaderOnceAndStayWithinBudget() {
        ContextItem a = item("a", "code", 1.0, 100);
        ContextItem b = item("b", "code", 0.9, 100);
        ContextItem c = item("c", "incident", 0.8, 100);

        ContextPackage pkg = packager.pack(request(10_000), List.of(a, b, c));

        assertEquals(cost(a, true), pkg.included().get(0).tokens());
        assertEquals(cost(b, false), pkg.included().get(1).tokens());
        assertEquals(cost(c, true), pkg.included().get(2).tokens());

        int exact = pkg.included().stream().mapToInt(ContextPackage.Entry::tokens).sum();
        ContextPackage tight = packager.pack(request(exact), List.of(a, b, c));
        assertEquals(3, tight.included().size());
        assertTrue(tight.usedTokens() <= exact);

        ContextPackage tooTight = packager.pack(request(exact - 1), List.of(a, b, c));
        assertEquals(List.of("c"), tooTight.omitted().stream().map(e -> e.item().id()).toList());
    }

    @Test
    void shouldGroupByDomainInOrderOfBestItem() {
        ContextPackage pkg = packager.pack(ContextRequest.of("t", 1), List.of(
                item("s1", "sql", 0.7, 10),
                item("c1", "code", 0.9, 10),
                item("c2", "code", 0.6, 10)));

        String rendered = pkg.rendered();
        assertTrue(rendered.startsWith("=== CODE CONTEXT ===\n\n[TEST] c1"));
        assertTrue(rendered.indexOf("[TEST] c2") < rendered.indexOf("=== SQL CONTEXT ==="));
        assertTrue(rendered.contains("source: origin-s1\n"));
    }

    @Test
    void shouldIncludeDuplicateIdsOnce() {
        ContextPackage pkg = packager.pack(ContextRequest.of("t", 1),
                List.of(item("a", "code", 0.9, 10), item("a", "code", 0.5, 10)));

        assertEquals(1, pkg.included().size());
        assertTrue(pkg.omitted().isEmpty());
    }

    @Test
    void shouldValidateInputs() {
        assertThrows(IllegalArgumentException.class, () -> ContextBudget.tokens(0));
        assertThrows(IllegalArgumentException.class, () -> new ContextRequest("t", -1, ContextBudget.unlimited()));
        assertThrows(IllegalArgumentException.class, () -> item("a", "code", 1.5, 1));
        assertEquals(3, TokenEstimator.characters(4).estimate("123456789"));
    }

    private int cost(ContextItem item, boolean withHeader) {
        return estimator.estimate(renderer.item(item))
                + (withHeader ? estimator.estimate(renderer.domainHeader(item.domain())) : 0);
    }

    private static ContextRequest request(int budget) {
        return new ContextRequest("t", 1, ContextBudget.tokens(budget));
    }

    private static List<String> ids(ContextPackage pkg) {
        return pkg.items().stream().map(ContextItem::id).toList();
    }

    private static ContextItem item(String id, String domain, double score, int contentChars) {
        return new ContextItem(id, domain, "TEST", id, "x".repeat(contentChars), score, "because",
                List.of("origin-" + id), Map.of());
    }
}
