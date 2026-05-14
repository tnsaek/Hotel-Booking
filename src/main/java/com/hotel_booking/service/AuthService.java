package com.hotel_booking.service;

import com.hotel_booking.dto.request.RegisterRequest;
import com.hotel_booking.dto.response.RegisterResponse;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);
}
