package com.hotel_booking.service.impl;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentGateway {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String stripeWebhookSecret;

    public Session createCheckoutSession(SessionCreateParams params) {
        configureStripe();
        try {
            return Session.create(params, stripeRequestOptions());
        } catch (StripeException e) {
            throw new IllegalStateException("Failed to create Stripe checkout session: " + e.getMessage(), e);
        }
    }

    public Session retrieveCheckoutSession(String sessionId) {
        configureStripe();
        try {
            return Session.retrieve(sessionId, stripeRequestOptions());
        } catch (StripeException e) {
            throw new IllegalStateException("Failed to retrieve Stripe checkout session: " + e.getMessage(), e);
        }
    }

    public Event constructWebhookEvent(String payload, String signatureHeader) throws SignatureVerificationException {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            throw new IllegalStateException("Stripe webhook secret is not configured");
        }
        return Webhook.constructEvent(payload, signatureHeader, stripeWebhookSecret);
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) {
        try {
            return PaymentIntent.retrieve(paymentIntentId, stripeRequestOptions());
        } catch (StripeException e) {
            throw new IllegalStateException("Failed to retrieve Stripe charge for refund: " + e.getMessage(), e);
        }
    }

    public Charge retrieveCharge(String chargeId) {
        try {
            return Charge.retrieve(chargeId, stripeRequestOptions());
        } catch (StripeException e) {
            throw new IllegalStateException("Failed to retrieve Stripe charge for refund: " + e.getMessage(), e);
        }
    }

    public void createRefund(RefundCreateParams params) {
        configureStripe();
        try {
            Refund.create(params, stripeRequestOptions());
        } catch (StripeException e) {
            throw new IllegalStateException("Failed to refund Stripe payment: " + e.getMessage(), e);
        }
    }

    private void configureStripe() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException("Stripe secret key is not configured");
        }
        Stripe.apiKey = stripeSecretKey;
    }

    private RequestOptions stripeRequestOptions() {
        return RequestOptions.builder()
                .setConnectTimeout(10_000)
                .setReadTimeout(10_000)
                .build();
    }
}
