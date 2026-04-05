package com.hotel_booking.service;

import com.hotel_booking.dto.HotelDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HotelService {
    HotelDto create(HotelDto dto);
    HotelDto get(Long id);
    Page<HotelDto> getAll(Pageable pageable);
    HotelDto update(Long id, HotelDto dto);
    void delete(Long id);
}
