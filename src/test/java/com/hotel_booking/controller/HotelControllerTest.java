package com.hotel_booking.controller;

import com.hotel_booking.dto.HotelDto;
import com.hotel_booking.service.HotelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelControllerTest {

    @Mock
    private HotelService hotelService;

    private HotelController controller;

    @BeforeEach
    void setUp() {
        controller = new HotelController(hotelService);
    }

    @Test
    void createReturnsCreatedResponseFromService() {
        HotelDto request = hotelDto(null, "Grand Hotel");
        HotelDto expected = hotelDto(1L, "Grand Hotel");

        when(hotelService.create(request)).thenReturn(expected);

        ResponseEntity<HotelDto> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
        verify(hotelService).create(request);
    }

    @Test
    void getReturnsHotelFromService() {
        HotelDto expected = hotelDto(1L, "Grand Hotel");

        when(hotelService.get(1L)).thenReturn(expected);

        HotelDto response = controller.get(1L);

        assertThat(response).isSameAs(expected);
        verify(hotelService).get(1L);
    }

    @Test
    void getAllReturnsPageFromService() {
        Pageable pageable = PageRequest.of(0, 2);
        Page<HotelDto> expected = new PageImpl<>(List.of(
                hotelDto(1L, "Grand Hotel"),
                hotelDto(2L, "City Hotel")
        ), pageable, 2);

        when(hotelService.getAll(pageable)).thenReturn(expected);

        Page<HotelDto> response = controller.getALL(pageable);

        assertThat(response).isSameAs(expected);
        verify(hotelService).getAll(pageable);
    }

    @Test
    void updateReturnsUpdatedHotelFromService() {
        HotelDto request = hotelDto(1L, "Updated Hotel");
        HotelDto expected = hotelDto(1L, "Updated Hotel");

        when(hotelService.update(1L, request)).thenReturn(expected);

        HotelDto response = controller.update(1L, request);

        assertThat(response).isSameAs(expected);
        verify(hotelService).update(1L, request);
    }

    @Test
    void deleteCallsServiceAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.delete(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(hotelService).delete(1L);
    }

    @Test
    void searchReturnsPageFromService() {
        Pageable pageable = PageRequest.of(1, 5);
        Page<HotelDto> expected = new PageImpl<>(List.of(hotelDto(1L, "Grand Hotel")), pageable, 1);

        when(hotelService.search("Grand", "Addis Ababa", pageable)).thenReturn(expected);

        Page<HotelDto> response = controller.search("Grand", "Addis Ababa", pageable);

        assertThat(response).isSameAs(expected);
        verify(hotelService).search("Grand", "Addis Ababa", pageable);
    }

    private HotelDto hotelDto(Long id, String name) {
        return HotelDto.builder()
                .id(id)
                .name(name)
                .location("Addis Ababa")
                .description("Central hotel")
                .build();
    }
}
