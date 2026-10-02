package com.example.payments.repository;

import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p WHERE p.status = :status")
    List<Payment> findByStatus(@Param("status") PaymentStatus status);
}
