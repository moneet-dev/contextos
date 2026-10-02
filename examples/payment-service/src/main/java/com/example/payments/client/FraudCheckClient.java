package com.example.payments.client;

public interface FraudCheckClient {

    boolean isAllowed(String customerId, long amountCents);
}
