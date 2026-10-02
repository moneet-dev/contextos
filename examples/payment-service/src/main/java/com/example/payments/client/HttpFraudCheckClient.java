package com.example.payments.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpFraudCheckClient implements FraudCheckClient {

    private static final long REVIEW_THRESHOLD_CENTS = 500_000;

    private final RestClient restClient;

    public HttpFraudCheckClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean isAllowed(String customerId, long amountCents) {
        if (amountCents < REVIEW_THRESHOLD_CENTS) {
            return true;
        }
        Boolean allowed = restClient.get()
                .uri("/fraud/customers/{id}/allowed?amount={amount}", customerId, amountCents)
                .retrieve()
                .body(Boolean.class);
        return Boolean.TRUE.equals(allowed);
    }
}
