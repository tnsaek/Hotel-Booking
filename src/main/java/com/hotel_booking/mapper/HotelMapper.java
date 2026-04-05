package com.hotel_booking.mapper;

import com.hotel_booking.dto.HotelDto;
import com.hotel_booking.entity.Hotel;
import org.springframework.stereotype.Component;

@Component
public class HotelMapper {
    public HotelDto toDto(Hotel hotel){
        return HotelDto.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .location(hotel.getLocation())
                .description(hotel.getDescription())
                .build();
    }

    public Hotel toEntity(HotelDto hotelDto){
        return Hotel.builder()
                .name(hotelDto.getName())
                .location(hotelDto.getLocation())
                .description(hotelDto.getDescription())
                .build();
    }
}
