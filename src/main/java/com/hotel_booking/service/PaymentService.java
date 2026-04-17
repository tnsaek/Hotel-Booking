package com.hotel_booking.service;

import com.hotel_booking.dto.request.PaymentRequest;
import com.hotel_booking.dto.response.PaymentResponse;

public interface PaymentService {
    PaymentResponse processPayment(PaymentRequest paymentRequest);
}
