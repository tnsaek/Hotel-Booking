package com.hotel_booking.service;

import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Payment;

public interface EmailService {
    void sendBookingConfirmationEmail(Booking booking, Payment payment);
}
