package com.hotel_booking.service.impl;

import com.stripe.Stripe;
import com.stripe.exception.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class StripePaymentGatewayTest {

    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new StripePaymentGateway();
        ReflectionTestUtils.setField(gateway, "stripeSecretKey", "sk_test_123");
        ReflectionTestUtils.setField(gateway, "stripeWebhookSecret", "whsec_123");
        Stripe.apiKey = null;
    }

    @AfterEach
    void tearDown() {
        Stripe.apiKey = null;
    }

    @Test
    void createCheckoutSessionConfiguresStripeAndReturnsSession() throws Exception {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("https://example.com/success")
                .setCancelUrl("https://example.com/cancel")
                .build();
        Session expected = new Session();

        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            sessionStatic.when(() -> Session.create(eq(params), any(RequestOptions.class))).thenReturn(expected);

            Session response = gateway.createCheckoutSession(params);

            assertThat(response).isSameAs(expected);
            assertThat(Stripe.apiKey).isEqualTo("sk_test_123");
        }
    }

    @Test
    void createCheckoutSessionWrapsStripeException() throws Exception {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("https://example.com/success")
                .setCancelUrl("https://example.com/cancel")
                .build();
        ApiException stripeException = stripeException("create failed");

        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            sessionStatic.when(() -> Session.create(eq(params), any(RequestOptions.class))).thenThrow(stripeException);

            assertThatThrownBy(() -> gateway.createCheckoutSession(params))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Failed to create Stripe checkout session: create failed")
                    .hasCause(stripeException);
        }
    }

    @Test
    void retrieveCheckoutSessionConfiguresStripeAndReturnsSession() throws Exception {
        Session expected = new Session();

        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            sessionStatic.when(() -> Session.retrieve(eq("cs_123"), any(RequestOptions.class))).thenReturn(expected);

            Session response = gateway.retrieveCheckoutSession("cs_123");

            assertThat(response).isSameAs(expected);
            assertThat(Stripe.apiKey).isEqualTo("sk_test_123");
        }
    }

    @Test
    void retrieveCheckoutSessionWrapsStripeException() throws Exception {
        ApiException stripeException = stripeException("retrieve failed");

        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            sessionStatic.when(() -> Session.retrieve(eq("cs_123"), any(RequestOptions.class))).thenThrow(stripeException);

            assertThatThrownBy(() -> gateway.retrieveCheckoutSession("cs_123"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Failed to retrieve Stripe checkout session: retrieve failed")
                    .hasCause(stripeException);
        }
    }

    @Test
    void constructWebhookEventReturnsStripeEvent() throws Exception {
        Event expected = mock(Event.class);

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent("payload", "signature", "whsec_123")).thenReturn(expected);

            Event response = gateway.constructWebhookEvent("payload", "signature");

            assertThat(response).isSameAs(expected);
        }
    }

    @Test
    void constructWebhookEventPropagatesSignatureVerificationException() throws Exception {
        SignatureVerificationException exception = new SignatureVerificationException("invalid", "signature");

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent("payload", "signature", "whsec_123")).thenThrow(exception);

            assertThatThrownBy(() -> gateway.constructWebhookEvent("payload", "signature"))
                    .isSameAs(exception);
        }
    }

    @Test
    void constructWebhookEventThrowsWhenWebhookSecretIsNull() {
        ReflectionTestUtils.setField(gateway, "stripeWebhookSecret", null);

        assertThatThrownBy(() -> gateway.constructWebhookEvent("payload", "signature"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe webhook secret is not configured");
    }

    @Test
    void constructWebhookEventThrowsWhenWebhookSecretIsBlank() {
        ReflectionTestUtils.setField(gateway, "stripeWebhookSecret", "   ");

        assertThatThrownBy(() -> gateway.constructWebhookEvent("payload", "signature"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe webhook secret is not configured");
    }

    @Test
    void retrievePaymentIntentReturnsPaymentIntent() throws Exception {
        PaymentIntent expected = new PaymentIntent();

        try (MockedStatic<PaymentIntent> paymentIntentStatic = mockStatic(PaymentIntent.class)) {
            paymentIntentStatic.when(() -> PaymentIntent.retrieve(eq("pi_123"), any(RequestOptions.class))).thenReturn(expected);

            PaymentIntent response = gateway.retrievePaymentIntent("pi_123");

            assertThat(response).isSameAs(expected);
        }
    }

    @Test
    void retrievePaymentIntentWrapsStripeException() throws Exception {
        ApiException stripeException = stripeException("payment intent missing");

        try (MockedStatic<PaymentIntent> paymentIntentStatic = mockStatic(PaymentIntent.class)) {
            paymentIntentStatic.when(() -> PaymentIntent.retrieve(eq("pi_123"), any(RequestOptions.class))).thenThrow(stripeException);

            assertThatThrownBy(() -> gateway.retrievePaymentIntent("pi_123"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Failed to retrieve Stripe charge for refund: payment intent missing")
                    .hasCause(stripeException);
        }
    }

    @Test
    void retrieveChargeReturnsCharge() throws Exception {
        Charge expected = new Charge();

        try (MockedStatic<Charge> chargeStatic = mockStatic(Charge.class)) {
            chargeStatic.when(() -> Charge.retrieve(eq("ch_123"), any(RequestOptions.class))).thenReturn(expected);

            Charge response = gateway.retrieveCharge("ch_123");

            assertThat(response).isSameAs(expected);
        }
    }

    @Test
    void retrieveChargeWrapsStripeException() throws Exception {
        ApiException stripeException = stripeException("charge missing");

        try (MockedStatic<Charge> chargeStatic = mockStatic(Charge.class)) {
            chargeStatic.when(() -> Charge.retrieve(eq("ch_123"), any(RequestOptions.class))).thenThrow(stripeException);

            assertThatThrownBy(() -> gateway.retrieveCharge("ch_123"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Failed to retrieve Stripe charge for refund: charge missing")
                    .hasCause(stripeException);
        }
    }

    @Test
    void createRefundConfiguresStripeAndCreatesRefund() throws Exception {
        RefundCreateParams params = RefundCreateParams.builder()
                .setCharge("ch_123")
                .build();
        Refund expected = new Refund();

        try (MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {
            refundStatic.when(() -> Refund.create(eq(params), any(RequestOptions.class))).thenReturn(expected);

            gateway.createRefund(params);

            assertThat(Stripe.apiKey).isEqualTo("sk_test_123");
        }
    }

    @Test
    void createRefundWrapsStripeException() throws Exception {
        RefundCreateParams params = RefundCreateParams.builder()
                .setCharge("ch_123")
                .build();
        ApiException stripeException = stripeException("refund failed");

        try (MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {
            refundStatic.when(() -> Refund.create(eq(params), any(RequestOptions.class))).thenThrow(stripeException);

            assertThatThrownBy(() -> gateway.createRefund(params))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Failed to refund Stripe payment: refund failed")
                    .hasCause(stripeException);
        }
    }

    @Test
    void createCheckoutSessionThrowsWhenStripeSecretKeyIsNull() {
        ReflectionTestUtils.setField(gateway, "stripeSecretKey", null);
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("https://example.com/success")
                .setCancelUrl("https://example.com/cancel")
                .build();

        assertThatThrownBy(() -> gateway.createCheckoutSession(params))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe secret key is not configured");
    }

    @Test
    void createRefundThrowsWhenStripeSecretKeyIsBlank() {
        ReflectionTestUtils.setField(gateway, "stripeSecretKey", "   ");
        RefundCreateParams params = RefundCreateParams.builder()
                .setCharge("ch_123")
                .build();

        assertThatThrownBy(() -> gateway.createRefund(params))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stripe secret key is not configured");
    }

    private ApiException stripeException(String message) {
        return new ApiException(message, "req_123", "code", 400, null);
    }
}
