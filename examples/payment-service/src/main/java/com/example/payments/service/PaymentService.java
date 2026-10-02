package com.example.payments.service;

import com.example.payments.client.FraudCheckClient;
import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentTransaction;
import com.example.payments.model.PaymentReceipt;
import com.example.payments.model.PaymentRequest;
import com.example.payments.repository.PaymentRepository;
import com.example.payments.repository.PaymentTransactionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final FraudCheckClient fraudCheckClient;
    private final JdbcTemplate jdbcTemplate;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentTransactionRepository transactionRepository,
                          FraudCheckClient fraudCheckClient,
                          JdbcTemplate jdbcTemplate) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.fraudCheckClient = fraudCheckClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public PaymentReceipt charge(PaymentRequest request) {
        if (!fraudCheckClient.isAllowed(request.customerId(), request.amountCents())) {
            throw new PaymentDeclinedException(request.customerId());
        }

        Payment payment = paymentRepository.save(
                Payment.pending(request.customerId(), request.amountCents()));

        transactionRepository.save(PaymentTransaction.attempt(payment, request.cardToken()));

        return new PaymentReceipt(payment.getId(), payment.getStatus());
    }

    @Transactional
    public void refund(long paymentId) {
        List<PaymentTransaction> history = transactionRepository.findByPaymentId(paymentId);
        if (history.isEmpty()) {
            throw new IllegalStateException("No transactions for payment " + paymentId);
        }

        jdbcTemplate.update(
                "UPDATE payments SET status = 'REFUNDED' WHERE id = ?", paymentId);
    }
}
