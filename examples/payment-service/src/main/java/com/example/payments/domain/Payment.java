package com.example.payments.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue
    private Long id;

    private String customerId;

    private long amountCents;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    protected Payment() {
    }

    public static Payment pending(String customerId, long amountCents) {
        Payment payment = new Payment();
        payment.customerId = customerId;
        payment.amountCents = amountCents;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public Long getId() {
        return id;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
