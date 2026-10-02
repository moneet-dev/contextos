package com.example.payments.model;

public record PaymentRequest(String customerId, long amountCents, String cardToken) {
}
