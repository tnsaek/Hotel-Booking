package com.hotel_booking.service.impl;

import com.hotel_booking.dto.request.PaymentRequest;
import com.hotel_booking.dto.response.PaymentResponse;
import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Payment;
import com.hotel_booking.entity.enums.BookingStatus;
import com.hotel_booking.entity.enums.PaymentStatus;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.repository.BookingRepository;
import com.hotel_booking.repository.PaymentRepository;
import com.hotel_booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    @Override
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        Booking booking = bookingRepository.findById(paymentRequest.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if(booking.getBookingStatus() != BookingStatus.PENDING){
            throw new IllegalStateException("Booking not in valid state");
        }
        Double amount = booking.getTotalPrice();
        Payment payment = Payment.builder()
                .amount(amount)
                .paymentStatus(PaymentStatus.SUCCESS)
                .transactionId(UUID.randomUUID().toString())
                .booking(booking)
                .build();

        paymentRepository.save(payment);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        return PaymentResponse.builder()
                .status(payment.getPaymentStatus().name())
                .transactionId(payment.getTransactionId())
                .build();
    }
}
