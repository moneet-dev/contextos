package com.example.payments.repository;

import com.example.payments.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    @Query(value = "SELECT * FROM payment_transactions WHERE payment_id = ?1 ORDER BY created_at",
           nativeQuery = true)
    List<PaymentTransaction> findByPaymentId(long paymentId);
}
