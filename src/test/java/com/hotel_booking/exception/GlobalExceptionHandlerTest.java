package com.hotel_booking.exception;

import com.hotel_booking.dto.response.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFoundReturnsNotFoundErrorResponse() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ResourceNotFoundException("hotel not found"));

        assertError(response, HttpStatus.NOT_FOUND, "hotel not found");
    }

    @Test
    void handleIllegalArgumentReturnsBadRequestErrorResponse() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException("invalid request"));

        assertError(response, HttpStatus.BAD_REQUEST, "invalid request");
    }

    @Test
    void handleIllegalStateReturnsBadRequestErrorResponse() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalState(new IllegalStateException("booking unavailable"));

        assertError(response, HttpStatus.BAD_REQUEST, "booking unavailable");
    }

    @Test
    void handleBadCredentialsReturnsUnauthorizedWithGenericMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadCredentials(new BadCredentialsException("raw auth failure"));

        assertError(response, HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @Test
    void handleValidationReturnsFirstFieldErrorMessage() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new SampleRequest(), "request");
        bindingResult.addError(new FieldError("request", "email", "Email must be valid"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(sampleMethodParameter(), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "Email must be valid");
    }

    @Test
    void handleValidationReturnsDefaultMessageWhenNoFieldErrorsExist() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new SampleRequest(), "request");
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(sampleMethodParameter(), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "Validation failed");
    }

    @Test
    void resourceNotFoundExceptionStoresMessage() {
        ResourceNotFoundException exception = new ResourceNotFoundException("user not found");

        assertThat(exception).hasMessage("user not found");
    }

    private void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(message);
    }

    private MethodParameter sampleMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleEndpoint", SampleRequest.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void sampleEndpoint(SampleRequest request) {
    }

    private static class SampleRequest {
    }
}
