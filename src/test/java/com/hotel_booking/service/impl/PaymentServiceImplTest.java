package com.hotel_booking.service.impl;

import com.hotel_booking.dto.request.PaymentRequest;
import com.hotel_booking.dto.response.PaymentResponse;
import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Payment;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.BookingStatus;
import com.hotel_booking.entity.enums.PaymentStatus;
import com.hotel_booking.entity.enums.RoomType;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.repository.BookingRepository;
import com.hotel_booking.repository.PaymentRepository;
import com.hotel_booking.service.EmailService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private StripePaymentGateway stripePaymentGateway;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, bookingRepository, emailService, stripePaymentGateway);
        ReflectionTestUtils.setField(service, "currency", "usd");
        ReflectionTestUtils.setField(service, "successUrl", "https://example.com/success");
        ReflectionTestUtils.setField(service, "cancelUrl", "https://example.com/cancel");
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void processPaymentCreatesNewInitiatedPaymentAndReturnsCheckoutUrl() {
        Booking booking = booking(10L, BookingStatus.PENDING, 200.0);
        Session session = session("cs_new", "https://checkout.example/new", "unpaid", "pi_new", 20_000L, bookingMetadata(booking.getId()));

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(stripePaymentGateway.createCheckoutSession(any(SessionCreateParams.class))).thenReturn(session);
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.empty());

        PaymentResponse response = service.processPayment(PaymentRequest.builder().bookingId(booking.getId()).build());

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.INITIATED.name());
        assertThat(response.getTransactionId()).isEqualTo("cs_new");
        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.example/new");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getUser()).isSameAs(booking.getUser());
        assertThat(savedPayment.getBooking()).isSameAs(booking);
        assertThat(savedPayment.getAmount()).isEqualTo(200.0);
        assertThat(savedPayment.getPaidAmount()).isZero();
        assertThat(savedPayment.getRefundedAmount()).isZero();
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.INITIATED);
    }

    @Test
    void processPaymentReusesExistingPayment() {
        Booking booking = booking(10L, BookingStatus.PENDING, 250.0);
        Payment existingPayment = payment(1L, booking, PaymentStatus.FAILED, 10.0, 0.0, 0.0, "old");
        Session session = session("cs_existing", "https://checkout.example/existing", "unpaid", "pi_existing", 25_000L, bookingMetadata(booking.getId()));

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(stripePaymentGateway.createCheckoutSession(any(SessionCreateParams.class))).thenReturn(session);
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(existingPayment));

        service.processPayment(PaymentRequest.builder().bookingId(booking.getId()).build());

        assertThat(existingPayment.getTransactionId()).isEqualTo("cs_existing");
        assertThat(existingPayment.getPaymentStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(existingPayment.getAmount()).isEqualTo(250.0);
        verify(paymentRepository).save(existingPayment);
    }

    @Test
    void processPaymentThrowsWhenBookingDoesNotExist() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processPayment(PaymentRequest.builder().bookingId(404L).build()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Booking not found");
        verifyNoInteractions(stripePaymentGateway);
    }

    @Test
    void processPaymentThrowsWhenBookingIsNotPending() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.processPayment(PaymentRequest.builder().bookingId(booking.getId()).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Booking not in valid state");
        verifyNoInteractions(stripePaymentGateway);
    }

    @Test
    void confirmStripeSessionThrowsWhenSessionIsNotPaid() {
        Session session = session("cs_unpaid", null, "unpaid", "pi_unpaid", 20_000L, bookingMetadata(10L));
        when(stripePaymentGateway.retrieveCheckoutSession("cs_unpaid")).thenReturn(session);

        assertThatThrownBy(() -> service.confirmStripeSession("cs_unpaid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe checkout session is not paid");
    }

    @Test
    void confirmStripeSessionCompletesRegularPayment() {
        Booking booking = booking(10L, BookingStatus.PENDING, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.INITIATED, 200.0, null, null, "cs_paid");
        Session session = session("cs_paid", null, "paid", "pi_paid", 20_000L, bookingMetadata(booking.getId()));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_paid")).thenReturn(session);
        when(paymentRepository.findByTransactionId("cs_paid")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        PaymentResponse response = service.confirmStripeSession("cs_paid");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS.name());
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaymentIntentId()).isEqualTo("pi_paid");
        assertThat(payment.getPaidAmount()).isEqualTo(200.0);
        assertThat(payment.getRefundedAmount()).isZero();
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(paymentRepository, atLeastOnce()).save(payment);
        verify(bookingRepository).save(booking);
        verify(emailService).sendBookingConfirmationEmail(booking, payment);
    }

    @Test
    void confirmStripeSessionCompletesRegularPaymentWithTargetBookingMetadata() {
        Booking booking = booking(10L, BookingStatus.PENDING, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.INITIATED, 200.0, 75.0, 5.0, "cs_paid");
        Map<String, String> metadata = targetMetadata(booking.getId(), 300.0, LocalDate.now().plusDays(10), LocalDate.now().plusDays(13));
        Session session = session("cs_paid", null, "paid", "pi_paid", 30_000L, metadata);

        when(stripePaymentGateway.retrieveCheckoutSession("cs_paid")).thenReturn(session);
        when(paymentRepository.findByTransactionId("cs_paid")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        service.confirmStripeSession("cs_paid");

        assertThat(payment.getPaidAmount()).isEqualTo(75.0);
        assertThat(payment.getRefundedAmount()).isEqualTo(5.0);
        assertThat(booking.getTotalPrice()).isEqualTo(300.0);
        assertThat(booking.getCheckInDate()).isEqualTo(LocalDate.parse(metadata.get("targetCheckIn")));
    }

    @Test
    void confirmStripeSessionCompletesPaymentWhenPaidAmountIsZeroAndBookingAlreadyConfirmed() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.INITIATED, 200.0, 0.0, 0.0, "cs_paid");
        Session session = session("cs_paid", null, "paid", "pi_paid", 20_000L, bookingMetadata(booking.getId()));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_paid")).thenReturn(session);
        when(paymentRepository.findByTransactionId("cs_paid")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        service.confirmStripeSession("cs_paid");

        assertThat(payment.getPaidAmount()).isEqualTo(200.0);
        verify(paymentRepository, atLeastOnce()).save(payment);
        verify(bookingRepository).save(booking);
    }

    @Test
    void confirmStripeSessionSendsEmailOnlyForAlreadyCompletedPayment() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "cs_paid");
        Session session = session("cs_paid", null, "paid", "pi_paid", 20_000L, bookingMetadata(booking.getId()));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_paid")).thenReturn(session);
        when(paymentRepository.findByTransactionId("cs_paid")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        service.confirmStripeSession("cs_paid");

        verify(paymentRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
        verify(emailService).sendBookingConfirmationEmail(booking, payment);
    }

    @Test
    void confirmStripeSessionThrowsWhenPaymentIsMissing() {
        Session session = session("cs_missing", null, "paid", "pi_paid", 20_000L, bookingMetadata(10L));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_missing")).thenReturn(session);
        when(paymentRepository.findByTransactionId("cs_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmStripeSession("cs_missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Payment not found");
    }

    @Test
    void confirmStripeSessionThrowsWhenBookingIsMissing() {
        Booking booking = booking(10L, BookingStatus.PENDING, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.INITIATED, 200.0, null, null, "cs_paid");
        Session session = session("cs_paid", null, "paid", "pi_paid", 20_000L, bookingMetadata(booking.getId()));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_paid")).thenReturn(session);
        when(paymentRepository.findByTransactionId("cs_paid")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmStripeSession("cs_paid"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Booking not found");
    }

    @Test
    void confirmStripeSessionCompletesBookingUpdateBalancePayment() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "cs_original");
        payment.setPendingTransactionId("cs_balance");
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(9);
        Session session = session("cs_balance", null, "paid", "pi_balance", 100_00L,
                updateBalanceMetadata(booking.getId(), 300.0, checkIn, checkOut));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_balance")).thenReturn(session);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentRepository.findByPendingTransactionId("cs_balance")).thenReturn(Optional.of(payment));

        service.confirmStripeSession("cs_balance");

        assertThat(payment.getPendingTransactionId()).isNull();
        assertThat(payment.getPaymentIntentId()).isEqualTo("pi_balance");
        assertThat(payment.getPaidAmount()).isEqualTo(300.0);
        assertThat(payment.getAmount()).isEqualTo(300.0);
        assertThat(booking.getCheckInDate()).isEqualTo(checkIn);
        assertThat(booking.getCheckOutDate()).isEqualTo(checkOut);
        verify(paymentRepository).save(payment);
        verify(bookingRepository).save(booking);
    }

    @Test
    void confirmStripeSessionCompletesBookingUpdateFromBookingPaymentFallback() {
        Booking booking = booking(10L, BookingStatus.PENDING, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.INITIATED, 200.0, null, null, "cs_original");
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(9);
        Session session = session("cs_balance", null, "paid", "pi_balance", 100_00L,
                updateBalanceMetadata(booking.getId(), 300.0, checkIn, checkOut));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_balance")).thenReturn(session);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentRepository.findByPendingTransactionId("cs_balance")).thenReturn(Optional.empty());
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        service.confirmStripeSession("cs_balance");

        assertThat(payment.getPaidAmount()).isEqualTo(300.0);
        verify(paymentRepository).save(payment);
    }

    @Test
    void confirmStripeSessionIgnoresAlreadyAppliedBalancePayment() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 300.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 300.0, 300.0, 0.0, "cs_original");
        Session session = session("cs_balance", null, "paid", "pi_balance", 100_00L,
                updateBalanceMetadata(booking.getId(), 300.0, booking.getCheckInDate(), booking.getCheckOutDate()));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_balance")).thenReturn(session);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentRepository.findByPendingTransactionId("cs_balance")).thenReturn(Optional.empty());
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        service.confirmStripeSession("cs_balance");

        verify(paymentRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmStripeSessionUpdateBalanceThrowsWhenBookingMissing() {
        Session session = session("cs_balance", null, "paid", "pi_balance", 100_00L,
                updateBalanceMetadata(10L, 300.0, LocalDate.now().plusDays(5), LocalDate.now().plusDays(9)));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_balance")).thenReturn(session);
        when(bookingRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmStripeSession("cs_balance"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Booking not found");
    }

    @Test
    void confirmStripeSessionUpdateBalanceThrowsWhenPaymentMissing() {
        Booking booking = booking(10L, BookingStatus.PENDING, 200.0);
        Session session = session("cs_balance", null, "paid", "pi_balance", 100_00L,
                updateBalanceMetadata(booking.getId(), 300.0, LocalDate.now().plusDays(5), LocalDate.now().plusDays(9)));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_balance")).thenReturn(session);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentRepository.findByPendingTransactionId("cs_balance")).thenReturn(Optional.empty());
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmStripeSession("cs_balance"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Payment not found");
    }

    @Test
    void handleStripeWebhookRejectsInvalidSignature() throws Exception {
        when(stripePaymentGateway.constructWebhookEvent("payload", "signature"))
                .thenThrow(new SignatureVerificationException("invalid", "signature"));

        assertThatThrownBy(() -> service.handleStripeWebhook("payload", "signature"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Stripe webhook signature");
    }

    @Test
    void handleStripeWebhookCompletesRegularPayment() throws Exception {
        Booking booking = booking(10L, BookingStatus.PENDING, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.INITIATED, 200.0, null, null, "cs_paid");
        Session session = session("cs_paid", null, "paid", "pi_paid", 20_000L, bookingMetadata(booking.getId()));

        Event event = event("checkout.session.completed", session);
        when(stripePaymentGateway.constructWebhookEvent("payload", "signature")).thenReturn(event);
        when(paymentRepository.findByTransactionId("cs_paid")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        service.handleStripeWebhook("payload", "signature");

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void handleStripeWebhookCompletesBalancePayment() throws Exception {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "cs_original");
        payment.setPendingTransactionId("cs_balance");
        Session session = session("cs_balance", null, "paid", "pi_balance", 10_000L,
                updateBalanceMetadata(booking.getId(), 300.0, LocalDate.now().plusDays(5), LocalDate.now().plusDays(9)));

        Event event = event("checkout.session.completed", session);
        when(stripePaymentGateway.constructWebhookEvent("payload", "signature")).thenReturn(event);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentRepository.findByPendingTransactionId("cs_balance")).thenReturn(Optional.of(payment));

        service.handleStripeWebhook("payload", "signature");

        assertThat(payment.getAmount()).isEqualTo(300.0);
    }

    @Test
    void handleStripeWebhookExpiresRegularAndPendingPayments() throws Exception {
        Booking booking = booking(10L, BookingStatus.PENDING, 200.0);
        Payment regularPayment = payment(1L, booking, PaymentStatus.INITIATED, 200.0, null, null, "cs_expired");
        Payment pendingPayment = payment(2L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "cs_original");
        pendingPayment.setPendingTransactionId("cs_expired");
        Session session = session("cs_expired", null, "unpaid", "pi_expired", 20_000L, bookingMetadata(booking.getId()));

        Event event = event("checkout.session.expired", session);
        when(stripePaymentGateway.constructWebhookEvent("payload", "signature")).thenReturn(event);
        when(paymentRepository.findByTransactionId("cs_expired")).thenReturn(Optional.of(regularPayment));
        when(paymentRepository.findByPendingTransactionId("cs_expired")).thenReturn(Optional.of(pendingPayment));

        service.handleStripeWebhook("payload", "signature");

        assertThat(regularPayment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pendingPayment.getPendingTransactionId()).isNull();
        verify(paymentRepository).save(regularPayment);
        verify(paymentRepository).save(pendingPayment);
    }

    @Test
    void handleStripeWebhookIgnoresUnhandledEventType() throws Exception {
        Event event = org.mockito.Mockito.mock(Event.class);
        when(event.getType()).thenReturn("customer.created");
        when(stripePaymentGateway.constructWebhookEvent("payload", "signature")).thenReturn(event);

        service.handleStripeWebhook("payload", "signature");

        verifyNoInteractions(paymentRepository, bookingRepository, emailService);
    }

    @Test
    void handleStripeWebhookThrowsWhenCheckoutSessionCannotBeDeserialized() throws Exception {
        EventDataObjectDeserializer deserializer = org.mockito.Mockito.mock(EventDataObjectDeserializer.class);
        Event event = org.mockito.Mockito.mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(stripePaymentGateway.constructWebhookEvent("payload", "signature")).thenReturn(event);

        assertThatThrownBy(() -> service.handleStripeWebhook("payload", "signature"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to deserialize Stripe checkout session");
    }

    @Test
    void refundPaymentForCancelledBookingDoesNothingWhenNoSuccessfulPaymentExists() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.empty());

        service.refundPaymentForCancelledBooking(booking);

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(stripePaymentGateway);
    }

    @Test
    void refundPaymentForCancelledBookingIgnoresNonSuccessfulPayment() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.FAILED, 200.0, 200.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        service.refundPaymentForCancelledBooking(booking);

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(stripePaymentGateway);
    }

    @Test
    void refundPaymentForCancelledBookingMarksAlreadyFullyRefundedPayment() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 0.0, 200.0, 200.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        service.refundPaymentForCancelledBooking(booking);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository, atLeastOnce()).save(payment);
        verifyNoInteractions(stripePaymentGateway);
    }

    @Test
    void refundPaymentForCancelledBookingRefundsSuccessfulPaymentByPaymentIntentId() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "pi_paid");
        PaymentIntent intent = paymentIntent("ch_paid");
        Charge charge = charge(20_000L, 5_000L);

        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(intent);
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge);

        service.refundPaymentForCancelledBooking(booking);

        assertThat(payment.getRefundedAmount()).isEqualTo(150.0);
        assertThat(payment.getAmount()).isZero();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(stripePaymentGateway).createRefund(any(RefundCreateParams.class));
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    void refundPaymentForCancelledBookingResolvesPaymentIntentFromCheckoutSession() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "cs_paid");
        Session session = session("cs_paid", null, "paid", "pi_from_session", 20_000L, bookingMetadata(booking.getId()));
        PaymentIntent intent = paymentIntent("ch_paid");
        Charge charge = charge(20_000L, 0L);

        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrieveCheckoutSession("cs_paid")).thenReturn(session);
        when(stripePaymentGateway.retrievePaymentIntent("pi_from_session")).thenReturn(intent);
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge);

        service.refundPaymentForCancelledBooking(booking);

        assertThat(payment.getPaymentIntentId()).isEqualTo("pi_from_session");
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    void refundPaymentForCancelledBookingThrowsWhenTransactionIdIsMissing() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, null);
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refundPaymentForCancelledBooking(booking))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment has no Stripe checkout session or payment intent id");
    }

    @Test
    void refundPaymentForCancelledBookingThrowsWhenTransactionIdIsBlank() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, " ");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refundPaymentForCancelledBooking(booking))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment has no Stripe checkout session or payment intent id");
    }

    @Test
    void refundPaymentForCancelledBookingThrowsWhenPaymentIntentIsBlank() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "cs_paid");
        payment.setPaymentIntentId(" ");
        Session session = session("cs_paid", null, "paid", " ", 20_000L, bookingMetadata(booking.getId()));
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrieveCheckoutSession("cs_paid")).thenReturn(session);

        assertThatThrownBy(() -> service.refundPaymentForCancelledBooking(booking))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe checkout session has no payment intent");
    }

    @Test
    void refundPaymentForCancelledBookingThrowsWhenCheckoutSessionHasNullPaymentIntent() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "cs_paid");
        Session session = session("cs_paid", null, "paid", null, 20_000L, bookingMetadata(booking.getId()));
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrieveCheckoutSession("cs_paid")).thenReturn(session);

        assertThatThrownBy(() -> service.refundPaymentForCancelledBooking(booking))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe checkout session has no payment intent");
    }

    @Test
    void refundPaymentForCancelledBookingThrowsWhenTransactionIdIsNotStripeId() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "manual_123");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refundPaymentForCancelledBooking(booking))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment transaction id is not a Stripe Checkout Session id: manual_123");
    }

    @Test
    void refundPaymentForCancelledBookingThrowsWhenStripeIntentHasNoCharge() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent(null));

        assertThatThrownBy(() -> service.refundPaymentForCancelledBooking(booking))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe payment intent has no latest charge");
    }

    @Test
    void refundPaymentForCancelledBookingThrowsWhenStripeIntentHasBlankCharge() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent(" "));

        assertThatThrownBy(() -> service.refundPaymentForCancelledBooking(booking))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe payment intent has no latest charge");
    }

    @Test
    void refundPaymentForCancelledBookingThrowsWhenStripeHasNoRefundableBalance() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(20_000L, 20_000L));

        assertThatThrownBy(() -> service.refundPaymentForCancelledBooking(booking))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe payment has no refundable balance");
    }

    @Test
    void reconcileCreatesBalanceCheckoutWhenNoPaymentExistsAndStayIsLonger() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        LocalDate newCheckOut = booking.getCheckOutDate().plusDays(2);
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.empty());
        when(stripePaymentGateway.createCheckoutSession(any(SessionCreateParams.class)))
                .thenReturn(session("cs_balance", "https://checkout.example/balance", "unpaid", null, 20_000L, bookingMetadata(booking.getId())));

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                400.0,
                booking.getCheckInDate(),
                newCheckOut
        );

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.INITIATED.name());
        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.example/balance");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void reconcileUsesRoomPriceForUnsuccessfulPaymentWhenOldStayHasNoNights() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 0.0);
        booking.setCheckOutDate(booking.getCheckInDate());
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.empty());
        when(stripePaymentGateway.createCheckoutSession(any(SessionCreateParams.class)))
                .thenReturn(session("cs_balance", "https://checkout.example/balance", "unpaid", null, 10_000L, bookingMetadata(booking.getId())));

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                0.0,
                100.0,
                booking.getCheckInDate(),
                booking.getCheckInDate().plusDays(1)
        );

        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.example/balance");
    }

    @Test
    void reconcileCreatesBalanceCheckoutForExistingUnsuccessfulPayment() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.FAILED, 200.0, null, null, "cs_old");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.createCheckoutSession(any(SessionCreateParams.class)))
                .thenReturn(session("cs_balance", "https://checkout.example/balance", "unpaid", null, 20_000L, bookingMetadata(booking.getId())));

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                400.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate().plusDays(2)
        );

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED.name());
        assertThat(payment.getPendingTransactionId()).isEqualTo("cs_balance");
    }

    @Test
    void reconcileUpdatesUnsuccessfulPaymentWhenNoAdditionalCheckoutIsNeeded() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.FAILED, 200.0, null, null, "cs_old");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                150.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );

        assertThat(response).isNull();
        assertThat(payment.getAmount()).isEqualTo(150.0);
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    void reconcileReturnsNullWhenNoPaymentExistsAndNoAdditionalCheckoutIsNeeded() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.empty());

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                150.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );

        assertThat(response).isNull();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void reconcileReturnsNullForUnpaidLongerStayWhenTotalDoesNotIncrease() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.empty());

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                200.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate().plusDays(1)
        );

        assertThat(response).isNull();
        verify(stripePaymentGateway, never()).createCheckoutSession(any(SessionCreateParams.class));
    }

    @Test
    void reconcileSuccessfulPaymentCreatesAdditionalCheckoutWhenNewTotalExceedsPaidBalance() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(20_000L, 0L));
        when(stripePaymentGateway.createCheckoutSession(any(SessionCreateParams.class)))
                .thenReturn(session("cs_balance", "https://checkout.example/balance", "unpaid", null, 20_000L, bookingMetadata(booking.getId())));

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                400.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate().plusDays(2)
        );

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS.name());
        assertThat(payment.getPendingTransactionId()).isEqualTo("cs_balance");
    }

    @Test
    void reconcileSuccessfulPaymentUsesRoomPriceWhenOldStayHasNoNights() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 0.0);
        booking.setCheckOutDate(booking.getCheckInDate());
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 0.0, 0.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(0L, 0L));
        when(stripePaymentGateway.createCheckoutSession(any(SessionCreateParams.class)))
                .thenReturn(session("cs_balance", "https://checkout.example/balance", "unpaid", null, 10_000L, bookingMetadata(booking.getId())));

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                0.0,
                100.0,
                booking.getCheckInDate(),
                booking.getCheckInDate().plusDays(1)
        );

        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.example/balance");
    }

    @Test
    void reconcileSuccessfulPaymentWithLongerStayAndCoveredTotalDoesNotCreateCheckoutOrRefund() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 500.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(50_000L, 0L));

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                400.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate().plusDays(2)
        );

        assertThat(response).isNull();
        verify(stripePaymentGateway, never()).createCheckoutSession(any(SessionCreateParams.class));
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    void reconcileSuccessfulPaymentWithLongerStayAndExactPaidBalanceDoesNotRefund() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 400.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(40_000L, 0L));

        service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                400.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate().plusDays(2)
        );

        verify(paymentRepository, after(200).never()).findById(payment.getId());
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    void reconcileSuccessfulPaymentWithShorterSameCostStayDoesNotRefund() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(20_000L, 0L));

        service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                200.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate().minusDays(1)
        );

        verify(paymentRepository, after(200).never()).findById(payment.getId());
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    void reconcileSuccessfulPaymentWithSameTotalAndSameStayDoesNotEnterRefundBranch() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(20_000L, 0L));

        service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                200.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );

        verify(paymentRepository, after(200).never()).findById(payment.getId());
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    void reconcileSuccessfulPaymentRefundsAfterCommitWhenStayGetsCheaper() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(20_000L, 0L));

        TransactionSynchronizationManager.initSynchronization();
        service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                150.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        TransactionSynchronizationManager.clearSynchronization();

        verify(paymentRepository, timeout(1000).atLeastOnce()).findById(payment.getId());
        verify(stripePaymentGateway, timeout(1000).atLeastOnce()).createRefund(any(RefundCreateParams.class));
        assertThat(payment.getAmount()).isEqualTo(150.0);
    }

    @Test
    void reconcileSuccessfulPaymentSchedulesRefundImmediatelyWithoutTransactionSynchronization() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.empty());
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(20_000L, 0L));

        service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                150.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );

        verify(paymentRepository, timeout(1000).atLeastOnce()).findById(payment.getId());
    }

    @Test
    void reconcileSuccessfulPaymentDoesNotRefundWhenRefundableAmountIsZero() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 200.0, "pi_paid");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));
        when(stripePaymentGateway.retrievePaymentIntent("pi_paid")).thenReturn(paymentIntent("ch_paid"));
        when(stripePaymentGateway.retrieveCharge("ch_paid")).thenReturn(charge(20_000L, 20_000L));

        service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                150.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );

        verify(paymentRepository, after(200).never()).findById(payment.getId());
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    void reconcileSuccessfulPaymentUsesLocalPaidBalanceWhenStripeLookupFails() {
        Booking booking = booking(10L, BookingStatus.CONFIRMED, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 220.0, 20.0, "bad_transaction");
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        PaymentResponse response = service.reconcilePaymentForUpdatedBooking(
                booking,
                200.0,
                200.0,
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );

        assertThat(response).isNull();
        assertThat(payment.getAmount()).isEqualTo(200.0);
    }

    @Test
    void confirmStripeSessionUpdateBalanceTreatsNullAmountAsZeroWhenCalculatingPaidAmount() {
        Booking booking = booking(10L, BookingStatus.PENDING, 0.0);
        Payment payment = payment(1L, booking, PaymentStatus.INITIATED, null, null, 25.0, "cs_original");
        Session session = session("cs_balance", null, "paid", "pi_balance", 10_000L,
                updateBalanceMetadata(booking.getId(), 100.0, booking.getCheckInDate(), booking.getCheckOutDate()));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_balance")).thenReturn(session);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentRepository.findByPendingTransactionId("cs_balance")).thenReturn(Optional.empty());
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        service.confirmStripeSession("cs_balance");

        assertThat(payment.getPaidAmount()).isEqualTo(100.0);
    }

    @Test
    void confirmStripeSessionUpdateBalanceTreatsZeroPaidAmountAsAmountPlusRefundedAmount() {
        Booking booking = booking(10L, BookingStatus.PENDING, 50.0);
        Payment payment = payment(1L, booking, PaymentStatus.INITIATED, 50.0, 0.0, 25.0, "cs_original");
        Session session = session("cs_balance", null, "paid", "pi_balance", 10_000L,
                updateBalanceMetadata(booking.getId(), 150.0, booking.getCheckInDate(), booking.getCheckOutDate()));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_balance")).thenReturn(session);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentRepository.findByPendingTransactionId("cs_balance")).thenReturn(Optional.empty());
        when(paymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        service.confirmStripeSession("cs_balance");

        assertThat(payment.getPaidAmount()).isEqualTo(175.0);
    }

    @Test
    void confirmStripeSessionWithSuccessfulPaymentAndPendingBookingCompletesPaymentAgain() {
        Booking booking = booking(10L, BookingStatus.PENDING, 200.0);
        Payment payment = payment(1L, booking, PaymentStatus.SUCCESS, 200.0, 200.0, 0.0, "cs_paid");
        Session session = session("cs_paid", null, "paid", "pi_paid", 20_000L, bookingMetadata(booking.getId()));

        when(stripePaymentGateway.retrieveCheckoutSession("cs_paid")).thenReturn(session);
        when(paymentRepository.findByTransactionId("cs_paid")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        service.confirmStripeSession("cs_paid");

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(paymentRepository).save(payment);
        verify(bookingRepository).save(booking);
    }

    private Event event(String type, Session session) {
        EventDataObjectDeserializer deserializer = org.mockito.Mockito.mock(EventDataObjectDeserializer.class);
        Event event = org.mockito.Mockito.mock(Event.class);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(session));
        return event;
    }

    private Session session(String id, String url, String paymentStatus, String paymentIntent, Long amountTotal, Map<String, String> metadata) {
        Session session = new Session();
        session.setId(id);
        session.setUrl(url);
        session.setPaymentStatus(paymentStatus);
        session.setPaymentIntent(paymentIntent);
        session.setAmountTotal(amountTotal);
        session.setMetadata(metadata);
        return session;
    }

    private PaymentIntent paymentIntent(String latestChargeId) {
        PaymentIntent paymentIntent = new PaymentIntent();
        paymentIntent.setLatestCharge(latestChargeId);
        return paymentIntent;
    }

    private Charge charge(Long amount, Long amountRefunded) {
        Charge charge = new Charge();
        charge.setAmount(amount);
        charge.setAmountRefunded(amountRefunded);
        return charge;
    }

    private Map<String, String> bookingMetadata(Long bookingId) {
        return Map.of("bookingId", String.valueOf(bookingId), "paymentPurpose", "BOOKING_PAYMENT");
    }

    private Map<String, String> targetMetadata(Long bookingId, Double targetTotal, LocalDate checkIn, LocalDate checkOut) {
        return Map.of(
                "bookingId", String.valueOf(bookingId),
                "paymentPurpose", "BOOKING_PAYMENT",
                "targetTotal", String.valueOf(targetTotal),
                "targetCheckIn", checkIn.toString(),
                "targetCheckOut", checkOut.toString()
        );
    }

    private Map<String, String> updateBalanceMetadata(Long bookingId, Double targetTotal, LocalDate checkIn, LocalDate checkOut) {
        return Map.of(
                "bookingId", String.valueOf(bookingId),
                "paymentPurpose", "BOOKING_UPDATE_BALANCE",
                "targetTotal", String.valueOf(targetTotal),
                "targetCheckIn", checkIn.toString(),
                "targetCheckOut", checkOut.toString()
        );
    }

    private Booking booking(Long id, BookingStatus status, Double totalPrice) {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        return Booking.builder()
                .id(id)
                .user(User.builder()
                        .id(2L)
                        .name("Test User")
                        .email("guest@example.com")
                        .phoneNumber("555-0100")
                        .role(UserRole.CUSTOMER)
                        .active(true)
                        .build())
                .room(Room.builder()
                        .id(3L)
                        .roomNumber(101)
                        .type(RoomType.SUITE)
                        .pricePerNight(100.0)
                        .available(true)
                        .hotel(Hotel.builder().id(4L).name("Test Hotel").location("Test City").build())
                        .build())
                .checkInDate(checkIn)
                .checkOutDate(checkIn.plusDays(2))
                .bookingStatus(status)
                .totalPrice(totalPrice)
                .build();
    }

    private Payment payment(
            Long id,
            Booking booking,
            PaymentStatus status,
            Double amount,
            Double paidAmount,
            Double refundedAmount,
            String transactionId
    ) {
        return Payment.builder()
                .id(id)
                .booking(booking)
                .user(booking.getUser())
                .paymentStatus(status)
                .amount(amount)
                .paidAmount(paidAmount)
                .refundedAmount(refundedAmount)
                .transactionId(transactionId)
                .build();
    }
}
