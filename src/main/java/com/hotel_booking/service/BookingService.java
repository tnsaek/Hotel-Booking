package com.hotel_booking.service;

import com.hotel_booking.dto.BookingRequest;
import com.hotel_booking.dto.BookingResponse;

public interface BookingService {
    BookingResponse createBooking(BookingRequest request);
    BookingResponse getBooking(Long id);
    void cancelBooking(Long id);
}
