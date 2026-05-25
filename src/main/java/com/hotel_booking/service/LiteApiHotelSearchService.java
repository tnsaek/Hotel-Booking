package com.hotel_booking.service;

import com.hotel_booking.dto.ExternalHotelOfferDto;

import java.time.LocalDate;
import java.util.List;

public interface LiteApiHotelSearchService {
    List<ExternalHotelOfferDto> search(
            String cityName,
            String countryCode,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Integer adults,
            Integer roomQuantity,
            String currency,
            String guestNationality
    );
}
