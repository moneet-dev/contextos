package com.example.payments.api;

import com.example.payments.model.PaymentReceipt;
import com.example.payments.model.PaymentRequest;
import com.example.payments.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentReceipt> pay(@RequestBody PaymentRequest request) {
        PaymentReceipt receipt = paymentService.charge(request);
        return ResponseEntity.ok(receipt);
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<Void> refund(@PathVariable long paymentId) {
        paymentService.refund(paymentId);
        return ResponseEntity.noContent().build();
    }
}
