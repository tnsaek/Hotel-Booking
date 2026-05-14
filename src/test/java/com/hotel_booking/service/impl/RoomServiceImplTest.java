package com.hotel_booking.service.impl;

import com.hotel_booking.dto.RoomDto;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.enums.RoomType;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.mapper.RoomMapper;
import com.hotel_booking.repository.HotelRepository;
import com.hotel_booking.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private HotelRepository hotelRepository;

    private RoomServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoomServiceImpl(roomRepository, hotelRepository, new RoomMapper());
    }

    @Test
    void createSavesMappedRoomAndReturnsSavedDto() {
        Hotel hotel = hotel(10L, "Grand Hotel");
        RoomDto request = roomDto(null, 101, RoomType.SUITE.name(), 150.0, true, "Lake view", hotel.getId());

        when(hotelRepository.findById(hotel.getId())).thenReturn(Optional.of(hotel));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(20L);
            return room;
        });

        RoomDto response = service.create(request);

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(roomDto(20L, 101, RoomType.SUITE.name(), 150.0, true, "Lake view", hotel.getId()));
    }

    @Test
    void createThrowsWhenHotelDoesNotExist() {
        RoomDto request = roomDto(null, 101, RoomType.SINGLE.name(), 80.0, true, "Compact", 404L);
        when(hotelRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Hotel not found");
        verifyNoInteractions(roomRepository);
    }

    @Test
    void getReturnsRoomWhenFound() {
        Room room = room(20L, 101, RoomType.DOUBLE, 120.0, true, "Balcony", hotel(10L, "Grand Hotel"));
        when(roomRepository.findById(20L)).thenReturn(Optional.of(room));

        RoomDto response = service.get(20L);

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(roomDto(20L, 101, RoomType.DOUBLE.name(), 120.0, true, "Balcony", 10L));
    }

    @Test
    void getThrowsWhenRoomDoesNotExist() {
        when(roomRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Room not found");
    }

    @Test
    void getByHotelReturnsMappedRooms() {
        Hotel hotel = hotel(10L, "Grand Hotel");
        when(roomRepository.findByHotelId(hotel.getId())).thenReturn(List.of(
                room(20L, 101, RoomType.SINGLE, 80.0, true, "Compact", hotel),
                room(21L, 102, RoomType.DOUBLE, 120.0, false, "Balcony", hotel)
        ));

        List<RoomDto> response = service.getByHotel(hotel.getId());

        assertThat(response).extracting(RoomDto::getId).containsExactly(20L, 21L);
        assertThat(response).extracting(RoomDto::getType).containsExactly(RoomType.SINGLE.name(), RoomType.DOUBLE.name());
    }

    @Test
    void updateChangesRoomWithoutChangingHotelWhenHotelIdIsNull() {
        Hotel existingHotel = hotel(10L, "Grand Hotel");
        Room room = room(20L, 101, RoomType.SINGLE, 80.0, true, "Compact", existingHotel);
        RoomDto request = roomDto(null, 201, RoomType.SUITE.name(), 200.0, false, "Penthouse", null);

        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        RoomDto response = service.update(room.getId(), request);

        assertThat(room.getHotel()).isSameAs(existingHotel);
        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(roomDto(20L, 201, RoomType.SUITE.name(), 200.0, false, "Penthouse", existingHotel.getId()));
        verifyNoInteractions(hotelRepository);
    }

    @Test
    void updateChangesRoomWithoutChangingHotelWhenHotelIdMatchesExistingHotel() {
        Hotel existingHotel = hotel(10L, "Grand Hotel");
        Room room = room(20L, 101, RoomType.SINGLE, 80.0, true, "Compact", existingHotel);
        RoomDto request = roomDto(null, 201, RoomType.DOUBLE.name(), 120.0, true, "Updated", existingHotel.getId());

        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        RoomDto response = service.update(room.getId(), request);

        assertThat(room.getHotel()).isSameAs(existingHotel);
        assertThat(response.getHotelId()).isEqualTo(existingHotel.getId());
        assertThat(response.getType()).isEqualTo(RoomType.DOUBLE.name());
        verifyNoInteractions(hotelRepository);
    }

    @Test
    void updateChangesRoomAndHotelWhenHotelIdDiffers() {
        Hotel existingHotel = hotel(10L, "Grand Hotel");
        Hotel newHotel = hotel(11L, "City Hotel");
        Room room = room(20L, 101, RoomType.SINGLE, 80.0, true, "Compact", existingHotel);
        RoomDto request = roomDto(null, 301, RoomType.SUITE.name(), 220.0, true, "Executive", newHotel.getId());

        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(hotelRepository.findById(newHotel.getId())).thenReturn(Optional.of(newHotel));

        RoomDto response = service.update(room.getId(), request);

        assertThat(room.getHotel()).isSameAs(newHotel);
        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(roomDto(20L, 301, RoomType.SUITE.name(), 220.0, true, "Executive", newHotel.getId()));
    }

    @Test
    void updateThrowsWhenRoomDoesNotExist() {
        when(roomRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, roomDto(null, 101, RoomType.SINGLE.name(), 80.0, true, "Compact", 10L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Room not found");
        verifyNoInteractions(hotelRepository);
    }

    @Test
    void updateThrowsWhenNewHotelDoesNotExist() {
        Hotel existingHotel = hotel(10L, "Grand Hotel");
        Room room = room(20L, 101, RoomType.SINGLE, 80.0, true, "Compact", existingHotel);
        RoomDto request = roomDto(null, 301, RoomType.SUITE.name(), 220.0, true, "Executive", 404L);

        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(hotelRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(room.getId(), request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Hotel not found");
    }

    @Test
    void deleteRemovesRoomWhenItExists() {
        when(roomRepository.existsById(20L)).thenReturn(true);

        service.delete(20L);

        verify(roomRepository).deleteById(20L);
    }

    @Test
    void deleteThrowsWhenRoomDoesNotExist() {
        when(roomRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Room not found");
        verify(roomRepository, never()).deleteById(404L);
    }

    private RoomDto roomDto(
            Long id,
            Integer roomNumber,
            String type,
            Double price,
            boolean available,
            String description,
            Long hotelId
    ) {
        return RoomDto.builder()
                .id(id)
                .roomNumber(roomNumber)
                .type(type)
                .price(price)
                .available(available)
                .description(description)
                .hotelId(hotelId)
                .build();
    }

    private Room room(
            Long id,
            Integer roomNumber,
            RoomType type,
            Double price,
            boolean available,
            String description,
            Hotel hotel
    ) {
        return Room.builder()
                .id(id)
                .roomNumber(roomNumber)
                .type(type)
                .pricePerNight(price)
                .available(available)
                .description(description)
                .hotel(hotel)
                .build();
    }

    private Hotel hotel(Long id, String name) {
        return Hotel.builder()
                .id(id)
                .name(name)
                .location("Test City")
                .description("Test description")
                .build();
    }
}
