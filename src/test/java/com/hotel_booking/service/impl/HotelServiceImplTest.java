package com.hotel_booking.service.impl;

import com.hotel_booking.dto.HotelDto;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.mapper.HotelMapper;
import com.hotel_booking.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelServiceImplTest {

    @Mock
    private HotelRepository hotelRepository;

    private HotelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HotelServiceImpl(hotelRepository, new HotelMapper());
    }

    @Test
    void createSavesMappedHotelAndReturnsSavedDto() {
        HotelDto request = hotelDto(null, "Grand Hotel", "Addis Ababa", "City center");
        when(hotelRepository.save(org.mockito.ArgumentMatchers.any(Hotel.class))).thenAnswer(invocation -> {
            Hotel hotel = invocation.getArgument(0);
            hotel.setId(10L);
            return hotel;
        });

        HotelDto response = service.create(request);

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(hotelDto(10L, "Grand Hotel", "Addis Ababa", "City center"));
    }

    @Test
    void getReturnsHotelWhenFound() {
        Hotel hotel = hotel(10L, "Grand Hotel", "Addis Ababa", "City center");
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));

        HotelDto response = service.get(10L);

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(hotelDto(10L, "Grand Hotel", "Addis Ababa", "City center"));
    }

    @Test
    void getThrowsWhenHotelDoesNotExist() {
        when(hotelRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Hotel not found");
    }

    @Test
    void getAllReturnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 2);
        when(hotelRepository.findAll(pageable)).thenReturn(new PageImpl<>(
                List.of(
                        hotel(1L, "North Hotel", "Bahir Dar", "Lake view"),
                        hotel(2L, "South Hotel", "Hawassa", "Resort")
                ),
                pageable,
                2
        ));

        Page<HotelDto> response = service.getAll(pageable);

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent())
                .extracting(HotelDto::getName)
                .containsExactly("North Hotel", "South Hotel");
    }

    @Test
    void updateChangesExistingHotelAndReturnsMappedDto() {
        Hotel hotel = hotel(10L, "Old Hotel", "Old City", "Old description");
        HotelDto request = hotelDto(null, "Updated Hotel", "Updated City", "Updated description");
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));

        HotelDto response = service.update(10L, request);

        assertThat(hotel.getName()).isEqualTo("Updated Hotel");
        assertThat(hotel.getLocation()).isEqualTo("Updated City");
        assertThat(hotel.getDescription()).isEqualTo("Updated description");
        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(hotelDto(10L, "Updated Hotel", "Updated City", "Updated description"));
    }

    @Test
    void updateThrowsWhenHotelDoesNotExist() {
        when(hotelRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, hotelDto(null, "Name", "Location", "Description")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Hotel not found");
    }

    @Test
    void deleteRemovesHotelWhenItExists() {
        when(hotelRepository.existsById(10L)).thenReturn(true);

        service.delete(10L);

        verify(hotelRepository).deleteById(10L);
    }

    @Test
    void deleteThrowsWhenHotelDoesNotExist() {
        when(hotelRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Hotel not found");
        verify(hotelRepository, never()).deleteById(404L);
    }

    @Test
    void searchByNameAndLocationUsesCombinedRepositoryQuery() {
        Pageable pageable = PageRequest.of(0, 5);
        when(hotelRepository.findByNameContainingIgnoreCaseAndLocationContainingIgnoreCase("grand", "addis", pageable))
                .thenReturn(pageOf(hotel(1L, "Grand Hotel", "Addis Ababa", "City center"), pageable));

        Page<HotelDto> response = service.search("grand", "addis", pageable);

        assertThat(response.getContent()).extracting(HotelDto::getId).containsExactly(1L);
    }

    @Test
    void searchByNameOnlyUsesNameRepositoryQuery() {
        Pageable pageable = PageRequest.of(0, 5);
        when(hotelRepository.findByNameContainingIgnoreCase("grand", pageable))
                .thenReturn(pageOf(hotel(1L, "Grand Hotel", "Addis Ababa", "City center"), pageable));

        Page<HotelDto> response = service.search("grand", "", pageable);

        assertThat(response.getContent()).extracting(HotelDto::getName).containsExactly("Grand Hotel");
    }

    @Test
    void searchByNameOnlyWhenLocationIsNullUsesNameRepositoryQuery() {
        Pageable pageable = PageRequest.of(0, 5);
        when(hotelRepository.findByNameContainingIgnoreCase("grand", pageable))
                .thenReturn(pageOf(hotel(1L, "Grand Hotel", "Addis Ababa", "City center"), pageable));

        Page<HotelDto> response = service.search("grand", null, pageable);

        assertThat(response.getContent()).extracting(HotelDto::getName).containsExactly("Grand Hotel");
    }

    @Test
    void searchByLocationOnlyUsesLocationRepositoryQuery() {
        Pageable pageable = PageRequest.of(0, 5);
        when(hotelRepository.findByLocationContainingIgnoreCase("addis", pageable))
                .thenReturn(pageOf(hotel(1L, "Grand Hotel", "Addis Ababa", "City center"), pageable));

        Page<HotelDto> response = service.search(null, "addis", pageable);

        assertThat(response.getContent()).extracting(HotelDto::getLocation).containsExactly("Addis Ababa");
    }

    @Test
    void searchWithNoCriteriaReturnsAllHotels() {
        Pageable pageable = PageRequest.of(0, 5);
        when(hotelRepository.findAll(pageable))
                .thenReturn(pageOf(hotel(1L, "Grand Hotel", "Addis Ababa", "City center"), pageable));

        Page<HotelDto> response = service.search("", null, pageable);

        assertThat(response.getContent()).extracting(HotelDto::getId).containsExactly(1L);
    }

    @Test
    void searchWithEmptyLocationAndNullNameReturnsAllHotels() {
        Pageable pageable = PageRequest.of(0, 5);
        when(hotelRepository.findAll(pageable))
                .thenReturn(pageOf(hotel(1L, "Grand Hotel", "Addis Ababa", "City center"), pageable));

        Page<HotelDto> response = service.search(null, "", pageable);

        assertThat(response.getContent()).extracting(HotelDto::getId).containsExactly(1L);
    }

    private Page<Hotel> pageOf(Hotel hotel, Pageable pageable) {
        return new PageImpl<>(List.of(hotel), pageable, 1);
    }

    private HotelDto hotelDto(Long id, String name, String location, String description) {
        return HotelDto.builder()
                .id(id)
                .name(name)
                .location(location)
                .description(description)
                .build();
    }

    private Hotel hotel(Long id, String name, String location, String description) {
        return Hotel.builder()
                .id(id)
                .name(name)
                .location(location)
                .description(description)
                .build();
    }
}
