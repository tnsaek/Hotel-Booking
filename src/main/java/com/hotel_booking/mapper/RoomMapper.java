package com.hotel_booking.mapper;

import com.hotel_booking.dto.RoomDto;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.enums.RoomType;
import org.springframework.stereotype.Component;

@Component
public class RoomMapper {
    public RoomDto toDto(Room room){
        return RoomDto.builder()
                .id(room.getId())
                .roomNumber(room.getRoomNumber())
                .type(room.getType().name())
                .price(room.getPricePerNight())
                .available(room.isAvailable())
                .description(room.getDescription())
                .hotelId(room.getHotel().getId())
                .build();
    }

    public Room toEntity(RoomDto dto, Hotel hotel){
        return Room.builder()
                .roomNumber(dto.getRoomNumber())
                .type(RoomType.valueOf(dto.getType()))
                .pricePerNight(dto.getPrice())
                .available(dto.isAvailable())
                .description(dto.getDescription())
                .hotel(hotel)
                .build();
    }
}
