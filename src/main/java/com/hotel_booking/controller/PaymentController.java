package com.hotel_booking.controller;

import com.hotel_booking.dto.request.PaymentRequest;
import com.hotel_booking.dto.response.PaymentResponse;
import com.hotel_booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest paymentRequest){
        return ResponseEntity.ok(paymentService.processPayment(paymentRequest));
    }
}
