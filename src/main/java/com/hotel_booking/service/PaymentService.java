package com.hotel_booking.service;

import com.hotel_booking.dto.request.PaymentRequest;
import com.hotel_booking.dto.response.PaymentResponse;
import com.hotel_booking.entity.Booking;

import java.time.LocalDate;

public interface PaymentService {
    PaymentResponse processPayment(PaymentRequest paymentRequest);
    PaymentResponse confirmStripeSession(String sessionId);
    void handleStripeWebhook(String payload, String signatureHeader);
    void refundPaymentForCancelledBooking(Booking booking);
    PaymentResponse reconcilePaymentForUpdatedBooking(
            Booking booking,
            double oldTotal,
            double newTotal,
            LocalDate newCheckIn,
            LocalDate newCheckOut
    );
}
