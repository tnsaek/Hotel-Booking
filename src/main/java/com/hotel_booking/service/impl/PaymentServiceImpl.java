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
import com.hotel_booking.service.EmailService;
import com.hotel_booking.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final EmailService emailService;
    private final StripePaymentGateway stripePaymentGateway;

    @Value("${stripe.currency}")
    private String currency;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        Booking booking = bookingRepository.findById(paymentRequest.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getBookingStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Booking not in valid state");
        }

        Session session = createCheckoutSession(booking);

        Payment payment = paymentRepository.findByBookingId(booking.getId())
                .orElseGet(() -> Payment.builder()
                        .user(booking.getUser())
                        .booking(booking)
                        .build());
        payment.setAmount(booking.getTotalPrice());
        payment.setPaidAmount(0.0);
        payment.setRefundedAmount(0.0);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setPaymentStatus(PaymentStatus.INITIATED);
        payment.setTransactionId(session.getId());

        paymentRepository.save(payment);

        return PaymentResponse.builder()
                .status(payment.getPaymentStatus().name())
                .transactionId(payment.getTransactionId())
                .checkoutUrl(session.getUrl())
                .build();
    }

    @Override
    @Transactional
    public PaymentResponse confirmStripeSession(String sessionId) {
        Session session = stripePaymentGateway.retrieveCheckoutSession(sessionId);

        if (!"paid".equals(session.getPaymentStatus())) {
            throw new IllegalStateException("Stripe checkout session is not paid");
        }

        if ("BOOKING_UPDATE_BALANCE".equals(session.getMetadata().get("paymentPurpose"))) {
            completeBookingUpdateBalancePayment(session);
        } else {
            completePayment(session);
        }

        return PaymentResponse.builder()
                .status(PaymentStatus.SUCCESS.name())
                .transactionId(session.getId())
                .build();
    }

    @Override
    @Transactional
    public void handleStripeWebhook(String payload, String signatureHeader) {
        Event event;
        try {
            event = stripePaymentGateway.constructWebhookEvent(payload, signatureHeader);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature", e);
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = extractCheckoutSession(event);
            if ("BOOKING_UPDATE_BALANCE".equals(session.getMetadata().get("paymentPurpose"))) {
                completeBookingUpdateBalancePayment(session);
            } else {
                completePayment(session);
            }
        } else if ("checkout.session.expired".equals(event.getType())) {
            Session session = extractCheckoutSession(event);
            failPayment(session.getId());
        }
    }

    @Override
    @Transactional
    public void refundPaymentForCancelledBooking(Booking booking) {
        paymentRepository.findByBookingId(booking.getId())
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.SUCCESS)
                .ifPresent(payment -> {
                    double refundableAmount = getRefundableAmount(payment);
                    if (refundableAmount <= 0) {
                        payment.setPaymentStatus(PaymentStatus.REFUNDED);
                        paymentRepository.save(payment);
                        return;
                    }

                    double refundedAmount = refundStripePayment(payment, refundableAmount);
                    payment.setRefundedAmount(getRefundedAmount(payment) + refundedAmount);
                    payment.setAmount(0.0);
                    payment.setPaymentStatus(PaymentStatus.REFUNDED);
                    payment.setPaymentDate(LocalDateTime.now());
                    paymentRepository.save(payment);
                });
    }

    @Override
    @Transactional
    public PaymentResponse reconcilePaymentForUpdatedBooking(
            Booking booking,
            double oldTotal,
            double newTotal,
            LocalDate newCheckIn,
            LocalDate newCheckOut
    ) {
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
        long oldNights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        long newNights = ChronoUnit.DAYS.between(newCheckIn, newCheckOut);
        boolean shorterOrSameStay = newNights <= oldNights;

        if (payment == null || payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            if (!shorterOrSameStay && newTotal > oldTotal) {
                double pricePerNight = oldNights > 0 ? oldTotal / oldNights : booking.getRoom().getPricePerNight();
                double additionalAmount = (newNights - oldNights) * pricePerNight;
                Session session = createCheckoutSession(
                        "Booking update balance #" + booking.getId(),
                        additionalAmount,
                        booking.getId(),
                        builder -> builder
                                .putMetadata("paymentPurpose", "BOOKING_UPDATE_BALANCE")
                                .putMetadata("targetTotal", String.valueOf(newTotal))
                                .putMetadata("targetCheckIn", newCheckIn.toString())
                                .putMetadata("targetCheckOut", newCheckOut.toString())
                );

                Payment updatedPayment = payment == null
                        ? Payment.builder()
                                .user(booking.getUser())
                                .booking(booking)
                                .build()
                        : payment;
                updatedPayment.setPendingTransactionId(session.getId());
                updatedPayment.setPaymentDate(LocalDateTime.now());
                paymentRepository.save(updatedPayment);

                return PaymentResponse.builder()
                        .status(updatedPayment.getPaymentStatus() == null ? PaymentStatus.INITIATED.name() : updatedPayment.getPaymentStatus().name())
                        .transactionId(updatedPayment.getTransactionId())
                        .checkoutUrl(session.getUrl())
                        .build();
            }

            if (payment != null) {
                payment.setAmount(newTotal);
                payment.setPaymentDate(LocalDateTime.now());
                paymentRepository.save(payment);
            }
            return null;
        }

        double paidBalance = getStripePaidBalance(payment);

        if (!shorterOrSameStay && newTotal > paidBalance) {
            double pricePerNight = oldNights > 0 ? oldTotal / oldNights : booking.getRoom().getPricePerNight();
            double additionalAmount = (newNights - oldNights) * pricePerNight;
            Session session = createCheckoutSession(
                    "Booking update balance #" + booking.getId(),
                    additionalAmount,
                    booking.getId(),
                    builder -> builder
                            .putMetadata("paymentPurpose", "BOOKING_UPDATE_BALANCE")
                            .putMetadata("targetTotal", String.valueOf(newTotal))
                            .putMetadata("targetCheckIn", newCheckIn.toString())
                            .putMetadata("targetCheckOut", newCheckOut.toString())
            );

            payment.setPendingTransactionId(session.getId());
            payment.setPaymentDate(LocalDateTime.now());
            paymentRepository.save(payment);

            return PaymentResponse.builder()
                    .status(payment.getPaymentStatus().name())
                    .transactionId(payment.getTransactionId())
                    .checkoutUrl(session.getUrl())
                    .build();
        }

        if (newTotal < paidBalance || shorterOrSameStay) {
            double refundAmount = Math.max(0, paidBalance - newTotal);
            double refundableAmount = Math.min(refundAmount, getRefundableAmount(payment));
            if (refundableAmount > 0) {
                scheduleRefundAfterCommit(payment.getId(), refundableAmount);
            }
        }

        payment.setAmount(newTotal);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);
        return null;
    }

    private Session createCheckoutSession(Booking booking) {
        return createCheckoutSession(
                "Hotel booking #" + booking.getId(),
                booking.getTotalPrice(),
                booking.getId(),
                builder -> builder.putMetadata("paymentPurpose", "BOOKING_PAYMENT")
        );
    }

    private Session createCheckoutSession(
            String productName,
            double amount,
            Long bookingId,
            java.util.function.Function<SessionCreateParams.Builder, SessionCreateParams.Builder> customizer
    ) {
        SessionCreateParams.LineItem.PriceData.ProductData productData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(productName)
                        .build();

        SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency)
                        .setUnitAmount(toMinorUnits(amount))
                        .setProductData(productData)
                        .build();

        SessionCreateParams.LineItem lineItem =
                SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(priceData)
                        .build();

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(String.valueOf(bookingId))
                .putMetadata("bookingId", String.valueOf(bookingId))
                .addLineItem(lineItem);

        SessionCreateParams params = customizer.apply(builder).build();

        return stripePaymentGateway.createCheckoutSession(params);
    }

    private void completePayment(Session session) {
        Long bookingId = Long.valueOf(session.getMetadata().get("bookingId"));
        Payment payment = paymentRepository.findByTransactionId(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS && booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            emailService.sendBookingConfirmationEmail(booking, payment);
            return;
        }

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentIntentId(session.getPaymentIntent());
        if (payment.getPaidAmount() == null || payment.getPaidAmount() == 0) {
            payment.setPaidAmount(payment.getAmount());
        }
        if (payment.getRefundedAmount() == null) {
            payment.setRefundedAmount(0.0);
        }
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        if (session.getMetadata().containsKey("targetTotal")) {
            booking.setTotalPrice(Double.parseDouble(session.getMetadata().get("targetTotal")));
        }
        if (session.getMetadata().containsKey("targetCheckIn")) {
            booking.setCheckInDate(LocalDate.parse(session.getMetadata().get("targetCheckIn")));
        }
        if (session.getMetadata().containsKey("targetCheckOut")) {
            booking.setCheckOutDate(LocalDate.parse(session.getMetadata().get("targetCheckOut")));
        }
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        emailService.sendBookingConfirmationEmail(booking, payment);
    }

    private void completeBookingUpdateBalancePayment(Session session) {
        Long bookingId = Long.valueOf(session.getMetadata().get("bookingId"));
        double targetTotal = Double.parseDouble(session.getMetadata().get("targetTotal"));
        LocalDate targetCheckIn = LocalDate.parse(session.getMetadata().get("targetCheckIn"));
        LocalDate targetCheckOut = LocalDate.parse(session.getMetadata().get("targetCheckOut"));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        Payment payment = paymentRepository.findByPendingTransactionId(session.getId())
                .orElseGet(() -> paymentRepository.findByBookingId(bookingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Payment not found")));

        if (payment.getPendingTransactionId() == null && booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            return;
        }

        payment.setPendingTransactionId(null);
        payment.setPaymentIntentId(session.getPaymentIntent());
        payment.setPaidAmount(getPaidAmount(payment) + session.getAmountTotal() / 100.0);
        payment.setAmount(targetTotal);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        booking.setCheckInDate(targetCheckIn);
        booking.setCheckOutDate(targetCheckOut);
        booking.setTotalPrice(targetTotal);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
    }

    private void failPayment(String transactionId) {
        paymentRepository.findByTransactionId(transactionId).ifPresent(payment -> {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setPaymentDate(LocalDateTime.now());
            paymentRepository.save(payment);
        });
        paymentRepository.findByPendingTransactionId(transactionId).ifPresent(payment -> {
            payment.setPendingTransactionId(null);
            payment.setPaymentDate(LocalDateTime.now());
            paymentRepository.save(payment);
        });
    }

    private double refundStripePayment(Payment payment, double amount) {
        String paymentIntentId = getPaymentIntentId(payment);
        long requestedAmount = toMinorUnits(amount);
        long refundableAmount = Math.min(requestedAmount, getStripeRefundableAmount(paymentIntentId));
        if (refundableAmount <= 0) {
            throw new IllegalStateException("Stripe payment has no refundable balance");
        }

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(refundableAmount)
                .putMetadata("bookingId", String.valueOf(payment.getBooking().getId()))
                .putMetadata("paymentId", String.valueOf(payment.getId()))
                .build();

        stripePaymentGateway.createRefund(params);
        return BigDecimal.valueOf(refundableAmount)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private long getStripeRefundableAmount(String paymentIntentId) {
        PaymentIntent paymentIntent = stripePaymentGateway.retrievePaymentIntent(paymentIntentId);
        String latestChargeId = paymentIntent.getLatestCharge();
        if (latestChargeId == null || latestChargeId.isBlank()) {
            throw new IllegalStateException("Stripe payment intent has no latest charge");
        }

        Charge charge = stripePaymentGateway.retrieveCharge(latestChargeId);
        return Math.max(0, charge.getAmount() - charge.getAmountRefunded());
    }

    private double getStripePaidBalance(Payment payment) {
        try {
            long refundableAmount = getStripeRefundableAmount(getPaymentIntentId(payment));
            return BigDecimal.valueOf(refundableAmount)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .doubleValue();
        } catch (RuntimeException exception) {
            return getPaidAmount(payment) - getRefundedAmount(payment);
        }
    }

    private void scheduleRefundAfterCommit(Long paymentId, double amount) {
        Runnable refundTask = () -> runRefund(paymentId, amount);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    new Thread(refundTask, "stripe-refund-" + paymentId).start();
                }
            });
            return;
        }

        new Thread(refundTask, "stripe-refund-" + paymentId).start();
    }

    private void runRefund(Long paymentId, double amount) {
        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
            double refundedAmount = refundStripePayment(payment, amount);
            payment.setRefundedAmount(getRefundedAmount(payment) + refundedAmount);
            payment.setPaymentDate(LocalDateTime.now());
            paymentRepository.save(payment);
        } catch (RuntimeException exception) {
            System.err.println("Failed to refund Stripe payment for payment " + paymentId + ": " + exception.getMessage());
        }
    }

    private String getPaymentIntentId(Payment payment) {
        if (payment.getPaymentIntentId() != null && !payment.getPaymentIntentId().isBlank()) {
            return payment.getPaymentIntentId();
        }

        if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
            throw new IllegalStateException("Payment has no Stripe checkout session or payment intent id");
        }

        if (payment.getTransactionId().startsWith("pi_")) {
            payment.setPaymentIntentId(payment.getTransactionId());
            paymentRepository.save(payment);
            return payment.getTransactionId();
        }

        if (!payment.getTransactionId().startsWith("cs_")) {
            throw new IllegalStateException("Payment transaction id is not a Stripe Checkout Session id: " + payment.getTransactionId());
        }

        Session session = stripePaymentGateway.retrieveCheckoutSession(payment.getTransactionId());
        String paymentIntentId = session.getPaymentIntent();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException("Stripe checkout session has no payment intent");
        }
        stripePaymentGateway.retrievePaymentIntent(paymentIntentId);
        payment.setPaymentIntentId(paymentIntentId);
        paymentRepository.save(payment);
        return paymentIntentId;
    }

    private double getRefundableAmount(Payment payment) {
        return Math.max(0, getPaidAmount(payment) - getRefundedAmount(payment));
    }

    private double getRefundedAmount(Payment payment) {
        return payment.getRefundedAmount() == null ? 0 : payment.getRefundedAmount();
    }

    private double getPaidAmount(Payment payment) {
        if (payment.getPaidAmount() != null && payment.getPaidAmount() > 0) {
            return payment.getPaidAmount();
        }
        return payment.getAmount() == null ? 0 : payment.getAmount() + getRefundedAmount(payment);
    }

    private Session extractCheckoutSession(Event event) {
        return (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new IllegalArgumentException("Unable to deserialize Stripe checkout session"));
    }

    private long toMinorUnits(Double amount) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
