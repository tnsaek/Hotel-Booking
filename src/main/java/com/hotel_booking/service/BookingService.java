package com.hotel_booking.service;

import com.hotel_booking.dto.request.BookingRequest;
import com.hotel_booking.dto.response.BookingResponse;

import java.util.List;

public interface BookingService {
    BookingResponse createBooking(BookingRequest request);
    BookingResponse getBooking(Long id);
    void cancelBooking(Long id);
    List<BookingResponse> getUserBookings(Long userId);
}
