package com.hotel_booking.mapper;

import com.hotel_booking.dto.HotelDto;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HotelMapperTest {

    private HotelMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new HotelMapper();
    }

    @Test
    void toDtoMapsHotelFields() {
        List<Room> rooms = List.of(Room.builder().id(1L).roomNumber(101).build());
        Hotel hotel = Hotel.builder()
                .id(5L)
                .name("Grand Hotel")
                .location("Addis Ababa")
                .description("Central hotel")
                .rooms(rooms)
                .build();

        HotelDto dto = mapper.toDto(hotel);

        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getName()).isEqualTo("Grand Hotel");
        assertThat(dto.getLocation()).isEqualTo("Addis Ababa");
        assertThat(dto.getDescription()).isEqualTo("Central hotel");
    }

    @Test
    void toEntityMapsDtoFieldsAndLeavesGeneratedFieldsUnset() {
        HotelDto dto = HotelDto.builder()
                .id(5L)
                .name("Grand Hotel")
                .location("Addis Ababa")
                .description("Central hotel")
                .build();

        Hotel hotel = mapper.toEntity(dto);

        assertThat(hotel.getId()).isNull();
        assertThat(hotel.getName()).isEqualTo("Grand Hotel");
        assertThat(hotel.getLocation()).isEqualTo("Addis Ababa");
        assertThat(hotel.getDescription()).isEqualTo("Central hotel");
        assertThat(hotel.getRooms()).isNull();
    }
}
