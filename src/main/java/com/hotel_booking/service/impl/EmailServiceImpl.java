package com.hotel_booking.service.impl;

import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Payment;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.User;
import com.hotel_booking.repository.BookingRepository;
import com.hotel_booking.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final BookingRepository bookingRepository;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Override
    public void sendBookingConfirmationEmail(Booking booking, Payment payment) {
        if (!mailEnabled || booking.isConfirmationEmailSent()) {
            return;
        }

        User customer = booking.getUser();
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            logger.warn("Booking confirmation email skipped for booking {} because customer email is missing", booking.getId());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) {
                message.setFrom(mailFrom);
            }
            message.setTo(customer.getEmail());
            message.setSubject("Booking confirmation #" + booking.getId());
            message.setText(buildBookingConfirmationBody(booking, payment));

            mailSender.send(message);

            booking.setConfirmationEmailSent(true);
            bookingRepository.save(booking);
        } catch (MailException exception) {
            logger.error("Failed to send booking confirmation email for booking {}", booking.getId(), exception);
        }
    }

    private String buildBookingConfirmationBody(Booking booking, Payment payment) {
        User customer = booking.getUser();
        Room room = booking.getRoom();
        Hotel hotel = room == null ? null : room.getHotel();

        String customerName = customer.getName() == null || customer.getName().isBlank()
                ? "Customer"
                : customer.getName();
        String hotelName = hotel == null ? "Hotel" : hotel.getName();
        String hotelLocation = hotel == null ? "N/A" : hotel.getLocation();
        String roomNumber = room == null || room.getRoomNumber() == null ? "N/A" : room.getRoomNumber().toString();
        String roomType = room == null || room.getType() == null ? "N/A" : room.getType().name();
        String paidAmount = payment == null || payment.getPaidAmount() == null
                ? String.valueOf(booking.getTotalPrice())
                : String.valueOf(payment.getPaidAmount());
        String paymentStatus = payment == null || payment.getPaymentStatus() == null
                ? "SUCCESS"
                : payment.getPaymentStatus().name();

        return """
                Hello %s,

                Your hotel booking has been confirmed.

                Booking ID: %s
                Hotel: %s
                Location: %s
                Room: %s
                Room Type: %s
                Check-in Date: %s
                Check-out Date: %s
                Amount Paid: %s
                Payment Status: %s

                Thank you for booking with us.
                """.formatted(
                customerName,
                booking.getId(),
                hotelName,
                hotelLocation,
                roomNumber,
                roomType,
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                paidAmount,
                paymentStatus
        );
    }
}
