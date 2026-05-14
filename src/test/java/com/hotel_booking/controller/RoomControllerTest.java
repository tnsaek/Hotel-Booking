package com.hotel_booking.controller;

import com.hotel_booking.dto.RoomDto;
import com.hotel_booking.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    private RoomController controller;

    @BeforeEach
    void setUp() {
        controller = new RoomController(roomService);
    }

    @Test
    void createReturnsCreatedResponseFromService() {
        RoomDto request = roomDto(null, 101);
        RoomDto expected = roomDto(1L, 101);

        when(roomService.create(request)).thenReturn(expected);

        ResponseEntity<RoomDto> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(roomService).create(request);
    }

    @Test
    void getReturnsRoomFromService() {
        RoomDto expected = roomDto(1L, 101);

        when(roomService.get(1L)).thenReturn(expected);

        RoomDto response = controller.get(1L);

        assertThat(response).isSameAs(expected);
        verify(roomService).get(1L);
    }

    @Test
    void getByHotelReturnsRoomsFromService() {
        List<RoomDto> expected = List.of(roomDto(1L, 101), roomDto(2L, 102));

        when(roomService.getByHotel(5L)).thenReturn(expected);

        List<RoomDto> response = controller.getByHotel(5L);

        assertThat(response).isSameAs(expected);
        verify(roomService).getByHotel(5L);
    }

    @Test
    void updateReturnsUpdatedRoomFromService() {
        RoomDto request = roomDto(1L, 201);
        RoomDto expected = roomDto(1L, 201);

        when(roomService.update(1L, request)).thenReturn(expected);

        RoomDto response = controller.update(1L, request);

        assertThat(response).isSameAs(expected);
        verify(roomService).update(1L, request);
    }

    @Test
    void deleteCallsServiceAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.delete(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(roomService).delete(1L);
    }

    private RoomDto roomDto(Long id, Integer roomNumber) {
        return RoomDto.builder()
                .id(id)
                .roomNumber(roomNumber)
                .type("DELUXE")
                .price(125.0)
                .available(true)
                .description("City view")
                .hotelId(5L)
                .build();
    }
}
