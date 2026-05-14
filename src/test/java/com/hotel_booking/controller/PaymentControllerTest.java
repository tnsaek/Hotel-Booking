package com.hotel_booking.controller;

import com.hotel_booking.dto.request.PaymentRequest;
import com.hotel_booking.dto.response.PaymentResponse;
import com.hotel_booking.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(paymentService);
    }

    @Test
    void processPaymentReturnsResponseFromService() {
        PaymentRequest request = PaymentRequest.builder()
                .bookingId(10L)
                .amount(250.0)
                .build();
        PaymentResponse expected = paymentResponse("PENDING", "txn_123");

        when(paymentService.processPayment(request)).thenReturn(expected);

        ResponseEntity<PaymentResponse> response = controller.processPayment(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(paymentService).processPayment(request);
    }

    @Test
    void confirmStripeSessionReturnsResponseFromService() {
        PaymentResponse expected = paymentResponse("SUCCESS", "pi_123");

        when(paymentService.confirmStripeSession("cs_test_123")).thenReturn(expected);

        ResponseEntity<PaymentResponse> response = controller.confirmStripeSession("cs_test_123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(paymentService).confirmStripeSession("cs_test_123");
    }

    @Test
    void handleStripeWebhookCallsServiceAndReturnsOk() {
        ResponseEntity<Void> response = controller.handleStripeWebhook("payload", "signature");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNull();
        verify(paymentService).handleStripeWebhook("payload", "signature");
    }

    private PaymentResponse paymentResponse(String status, String transactionId) {
        return PaymentResponse.builder()
                .status(status)
                .transactionId(transactionId)
                .checkoutUrl("https://checkout.example/session")
                .build();
    }
}
