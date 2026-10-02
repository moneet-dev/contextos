package com.example.payments.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Payment payment;

    private String cardToken;

    private Instant createdAt;

    protected PaymentTransaction() {
    }

    public static PaymentTransaction attempt(Payment payment, String cardToken) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.payment = payment;
        transaction.cardToken = cardToken;
        transaction.createdAt = Instant.now();
        return transaction;
    }
}
