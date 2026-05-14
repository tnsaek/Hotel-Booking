package com.hotel_booking.mapper;

import com.hotel_booking.dto.RoomDto;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.enums.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomMapperTest {

    private RoomMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new RoomMapper();
    }

    @Test
    void toDtoMapsRoomAndHotelFields() {
        Hotel hotel = hotel();
        Room room = Room.builder()
                .id(10L)
                .roomNumber(101)
                .type(RoomType.SUITE)
                .pricePerNight(125.0)
                .available(true)
                .description("City view")
                .hotel(hotel)
                .build();

        RoomDto dto = mapper.toDto(room);

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getRoomNumber()).isEqualTo(101);
        assertThat(dto.getType()).isEqualTo("SUITE");
        assertThat(dto.getPrice()).isEqualTo(125.0);
        assertThat(dto.isAvailable()).isTrue();
        assertThat(dto.getDescription()).isEqualTo("City view");
        assertThat(dto.getHotelId()).isEqualTo(5L);
    }

    @Test
    void toEntityMapsDtoFieldsAndHotel() {
        Hotel hotel = hotel();
        RoomDto dto = RoomDto.builder()
                .id(10L)
                .roomNumber(202)
                .type("DOUBLE")
                .price(95.0)
                .available(false)
                .description("Courtyard room")
                .hotelId(hotel.getId())
                .build();

        Room room = mapper.toEntity(dto, hotel);

        assertThat(room.getId()).isNull();
        assertThat(room.getRoomNumber()).isEqualTo(202);
        assertThat(room.getType()).isEqualTo(RoomType.DOUBLE);
        assertThat(room.getPricePerNight()).isEqualTo(95.0);
        assertThat(room.isAvailable()).isFalse();
        assertThat(room.getDescription()).isEqualTo("Courtyard room");
        assertThat(room.getHotel()).isSameAs(hotel);
        assertThat(room.getBookings()).isNull();
    }

    @Test
    void toEntityThrowsWhenDtoTypeIsInvalid() {
        RoomDto dto = RoomDto.builder()
                .roomNumber(303)
                .type("PENTHOUSE")
                .price(250.0)
                .available(true)
                .description("Invalid room type")
                .hotelId(5L)
                .build();

        assertThatThrownBy(() -> mapper.toEntity(dto, hotel()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Hotel hotel() {
        return Hotel.builder()
                .id(5L)
                .name("Grand Hotel")
                .location("Addis Ababa")
                .description("Central hotel")
                .build();
    }
}
