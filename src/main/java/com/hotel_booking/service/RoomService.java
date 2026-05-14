package com.hotel_booking.service;

import com.hotel_booking.dto.RoomDto;

import java.util.List;

public interface RoomService {
    RoomDto create(RoomDto dto);
    RoomDto get(Long id);
    List<RoomDto> getByHotel(Long hotelId);
    RoomDto update(Long id, RoomDto dto);
    void delete(Long id);
}
