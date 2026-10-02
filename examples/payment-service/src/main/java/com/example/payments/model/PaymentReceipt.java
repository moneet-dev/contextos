package com.example.payments.model;

import com.example.payments.domain.PaymentStatus;

public record PaymentReceipt(Long paymentId, PaymentStatus status) {
}
