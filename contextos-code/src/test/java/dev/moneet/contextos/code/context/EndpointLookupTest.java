package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.source.JavaRepositorySource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EndpointLookupTest {

    private static EndpointLookup endpoints;

    @BeforeAll
    static void load() {
        CodeRepository repository = new JavaRepositorySource(
                Path.of(System.getProperty("contextos.examples"), "payment-service")).load();
        endpoints = new EndpointLookup(repository);
    }

    @Test
    void shouldCombineClassAndMethodMappings() {
        assertEquals("PaymentController#pay(PaymentRequest)", handler("POST /payments"));
        assertEquals("PaymentController#pay(PaymentRequest)", handler("post /payments/"));
    }

    @Test
    void shouldMatchPathVariablesByPosition() {
        assertEquals("PaymentController#refund(long)", handler("POST /payments/{id}/refund"));
    }

    @Test
    void shouldRequireMatchingMethodAndPath() {
        assertTrue(endpoints.find("GET /payments").isEmpty());
        assertTrue(endpoints.find("POST /check").isEmpty());
        assertTrue(endpoints.find("SELECT payment_transactions").isEmpty());
    }

    @Test
    void shouldHandleRequestMappingWithAndWithoutMethod(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("Api.java"), """
                @RequestMapping(path = "/orders/")
                class Api {
                    @RequestMapping(value = "/{orderId}", method = RequestMethod.DELETE)
                    void cancel(long orderId) { }

                    @RequestMapping("/search")
                    void search() { }

                    @GetMapping
                    void list() { }
                }
                """);
        EndpointLookup lookup = new EndpointLookup(new JavaRepositorySource(root).load());

        assertEquals("Api#cancel(long)", lookup.find("DELETE /orders/{id}").map(Symbol::displayName).orElseThrow());
        assertTrue(lookup.find("DELETE /orders/42").isEmpty(), "routes are matched as templates, not concrete paths");
        assertTrue(lookup.find("GET /orders/{id}").isEmpty());
        assertEquals("Api#search()", lookup.find("PUT /orders/search").map(Symbol::displayName).orElseThrow());
        assertEquals("Api#list()", lookup.find("GET /orders").map(Symbol::displayName).orElseThrow());
    }

    private static String handler(String operation) {
        Optional<Symbol> symbol = endpoints.find(operation);
        return symbol.map(Symbol::displayName).orElse(null);
    }
}
