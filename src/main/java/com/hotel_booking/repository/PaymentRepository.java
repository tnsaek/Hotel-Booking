package com.hotel_booking.repository;

import com.hotel_booking.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionId(String transactionId);
    Optional<Payment> findByPendingTransactionId(String pendingTransactionId);
    Optional<Payment> findByBookingId(Long bookingId);
}
