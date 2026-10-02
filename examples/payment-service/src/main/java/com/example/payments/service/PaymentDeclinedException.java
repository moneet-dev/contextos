package com.example.payments.service;

public class PaymentDeclinedException extends RuntimeException {

    public PaymentDeclinedException(String customerId) {
        super("Payment declined for customer " + customerId);
    }
}
