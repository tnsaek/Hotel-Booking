package com.hotel_booking.service.impl;

import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Payment;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.PaymentStatus;
import com.hotel_booking.entity.enums.RoomType;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private BookingRepository bookingRepository;

    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailServiceImpl(mailSender, bookingRepository);
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@example.com");
    }

    @Test
    void sendBookingConfirmationEmailSendsMessageAndMarksBookingSent() {
        Booking booking = bookingWithRoomAndHotel();
        Payment payment = payment(325.75, PaymentStatus.SUCCESS);

        service.sendBookingConfirmationEmail(booking, payment);

        SimpleMailMessage message = sentMessage();
        assertThat(message.getFrom()).isEqualTo("noreply@example.com");
        assertThat(message.getTo()).containsExactly("guest@example.com");
        assertThat(message.getSubject()).isEqualTo("Booking confirmation #10");
        assertThat(message.getText())
                .contains("Hello Jane Doe,")
                .contains("Booking ID: 10")
                .contains("Hotel: Grand Hotel")
                .contains("Location: Addis Ababa")
                .contains("Room: 101")
                .contains("Room Type: SUITE")
                .contains("Check-in Date: " + booking.getCheckInDate())
                .contains("Check-out Date: " + booking.getCheckOutDate())
                .contains("Amount Paid: 325.75")
                .contains("Payment Status: SUCCESS");

        assertThat(booking.isConfirmationEmailSent()).isTrue();
        verify(bookingRepository).save(booking);
    }

    @Test
    void sendBookingConfirmationEmailDoesNothingWhenMailIsDisabled() {
        ReflectionTestUtils.setField(service, "mailEnabled", false);
        Booking booking = bookingWithRoomAndHotel();

        service.sendBookingConfirmationEmail(booking, payment(200.0, PaymentStatus.SUCCESS));

        verifyNoInteractions(mailSender, bookingRepository);
        assertThat(booking.isConfirmationEmailSent()).isFalse();
    }

    @Test
    void sendBookingConfirmationEmailDoesNothingWhenAlreadySent() {
        Booking booking = bookingWithRoomAndHotel();
        booking.setConfirmationEmailSent(true);

        service.sendBookingConfirmationEmail(booking, payment(200.0, PaymentStatus.SUCCESS));

        verifyNoInteractions(mailSender, bookingRepository);
    }

    @Test
    void sendBookingConfirmationEmailSkipsWhenCustomerIsMissing() {
        Booking booking = bookingWithRoomAndHotel();
        booking.setUser(null);

        service.sendBookingConfirmationEmail(booking, payment(200.0, PaymentStatus.SUCCESS));

        verifyNoInteractions(mailSender, bookingRepository);
    }

    @Test
    void sendBookingConfirmationEmailSkipsWhenCustomerEmailIsNull() {
        Booking booking = bookingWithRoomAndHotel();
        booking.getUser().setEmail(null);

        service.sendBookingConfirmationEmail(booking, payment(200.0, PaymentStatus.SUCCESS));

        verifyNoInteractions(mailSender, bookingRepository);
    }

    @Test
    void sendBookingConfirmationEmailSkipsWhenCustomerEmailIsBlank() {
        Booking booking = bookingWithRoomAndHotel();
        booking.getUser().setEmail(" ");

        service.sendBookingConfirmationEmail(booking, payment(200.0, PaymentStatus.SUCCESS));

        verifyNoInteractions(mailSender, bookingRepository);
    }

    @Test
    void sendBookingConfirmationEmailOmitsFromWhenMailFromIsNull() {
        ReflectionTestUtils.setField(service, "mailFrom", null);
        Booking booking = bookingWithRoomAndHotel();

        service.sendBookingConfirmationEmail(booking, payment(200.0, PaymentStatus.SUCCESS));

        assertThat(sentMessage().getFrom()).isNull();
    }

    @Test
    void sendBookingConfirmationEmailOmitsFromWhenMailFromIsBlank() {
        ReflectionTestUtils.setField(service, "mailFrom", " ");
        Booking booking = bookingWithRoomAndHotel();

        service.sendBookingConfirmationEmail(booking, payment(200.0, PaymentStatus.SUCCESS));

        assertThat(sentMessage().getFrom()).isNull();
    }

    @Test
    void sendBookingConfirmationEmailUsesFallbackValuesWhenRoomAndPaymentAreMissing() {
        Booking booking = bookingWithRoomAndHotel();
        booking.setRoom(null);
        booking.getUser().setName(null);

        service.sendBookingConfirmationEmail(booking, null);

        assertThat(sentMessage().getText())
                .contains("Hello Customer,")
                .contains("Hotel: Hotel")
                .contains("Location: N/A")
                .contains("Room: N/A")
                .contains("Room Type: N/A")
                .contains("Amount Paid: 300.0")
                .contains("Payment Status: SUCCESS");
    }

    @Test
    void sendBookingConfirmationEmailUsesFallbackValuesWhenNestedRoomFieldsAndPaymentFieldsAreMissing() {
        Booking booking = bookingWithRoomAndHotel();
        booking.getUser().setName(" ");
        booking.getRoom().setHotel(null);
        booking.getRoom().setRoomNumber(null);
        booking.getRoom().setType(null);
        Payment payment = payment(null, null);

        service.sendBookingConfirmationEmail(booking, payment);

        assertThat(sentMessage().getText())
                .contains("Hello Customer,")
                .contains("Hotel: Hotel")
                .contains("Location: N/A")
                .contains("Room: N/A")
                .contains("Room Type: N/A")
                .contains("Amount Paid: 300.0")
                .contains("Payment Status: SUCCESS");
    }

    @Test
    void sendBookingConfirmationEmailLogsAndDoesNotMarkSentWhenMailSenderFails() {
        Booking booking = bookingWithRoomAndHotel();
        doThrow(new MailSendException("smtp down")).when(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        service.sendBookingConfirmationEmail(booking, payment(200.0, PaymentStatus.SUCCESS));

        assertThat(booking.isConfirmationEmailSent()).isFalse();
        verify(bookingRepository, never()).save(booking);
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        return messageCaptor.getValue();
    }

    private Booking bookingWithRoomAndHotel() {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        return Booking.builder()
                .id(10L)
                .user(User.builder()
                        .id(1L)
                        .name("Jane Doe")
                        .email("guest@example.com")
                        .phoneNumber("555-0100")
                        .role(UserRole.CUSTOMER)
                        .active(true)
                        .build())
                .room(Room.builder()
                        .id(20L)
                        .roomNumber(101)
                        .type(RoomType.SUITE)
                        .pricePerNight(150.0)
                        .available(true)
                        .hotel(Hotel.builder()
                                .id(30L)
                                .name("Grand Hotel")
                                .location("Addis Ababa")
                                .description("City center")
                                .build())
                        .build())
                .checkInDate(checkIn)
                .checkOutDate(checkIn.plusDays(2))
                .totalPrice(300.0)
                .confirmationEmailSent(false)
                .build();
    }

    private Payment payment(Double paidAmount, PaymentStatus status) {
        return Payment.builder()
                .id(40L)
                .paidAmount(paidAmount)
                .paymentStatus(status)
                .build();
    }
}
