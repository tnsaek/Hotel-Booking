package com.hotel_booking.controller;

import com.hotel_booking.dto.request.BookingRequest;
import com.hotel_booking.dto.request.BookingUpdateRequest;
import com.hotel_booking.dto.response.BookingResponse;
import com.hotel_booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    private BookingController controller;

    @BeforeEach
    void setUp() {
        controller = new BookingController(bookingService);
    }

    @Test
    void createReturnsCreatedResponseFromService() {
        BookingRequest request = BookingRequest.builder()
                .userId(1L)
                .roomId(2L)
                .checkIn(LocalDate.of(2026, 6, 1))
                .checkOut(LocalDate.of(2026, 6, 3))
                .build();
        BookingResponse expected = bookingResponse(10L);

        when(bookingService.createBooking(request)).thenReturn(expected);

        ResponseEntity<BookingResponse> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(bookingService).createBooking(request);
    }

    @Test
    void getReturnsBookingFromService() {
        BookingResponse expected = bookingResponse(10L);

        when(bookingService.getBooking(10L)).thenReturn(expected);

        ResponseEntity<BookingResponse> response = controller.get(10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(bookingService).getBooking(10L);
    }

    @Test
    void updateReturnsUpdatedBookingFromService() {
        BookingUpdateRequest request = BookingUpdateRequest.builder()
                .checkIn(LocalDate.of(2026, 7, 1))
                .checkOut(LocalDate.of(2026, 7, 4))
                .build();
        BookingResponse expected = bookingResponse(10L);

        when(bookingService.updateBooking(10L, request)).thenReturn(expected);

        ResponseEntity<BookingResponse> response = controller.update(10L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(bookingService).updateBooking(10L, request);
    }

    @Test
    void cancelCallsServiceAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.cancel(10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(bookingService).cancelBooking(10L);
    }

    @Test
    void getUserBookingsReturnsListFromService() {
        List<BookingResponse> expected = List.of(bookingResponse(10L), bookingResponse(11L));

        when(bookingService.getUserBookings(3L)).thenReturn(expected);

        List<BookingResponse> response = controller.getUserBookings(3L);

        assertThat(response).isSameAs(expected);
        verify(bookingService).getUserBookings(3L);
    }

    private BookingResponse bookingResponse(Long id) {
        return BookingResponse.builder()
                .bookingId(id)
                .status("CONFIRMED")
                .totalAmount(250.0)
                .checkIn(LocalDate.of(2026, 6, 1))
                .checkOut(LocalDate.of(2026, 6, 3))
                .roomId(2L)
                .roomNumber(101)
                .roomType("DELUXE")
                .hotelName("Grand Hotel")
                .paymentRequired(false)
                .additionalAmount(0.0)
                .checkoutUrl("https://checkout.example/booking/" + id)
                .build();
    }
}
