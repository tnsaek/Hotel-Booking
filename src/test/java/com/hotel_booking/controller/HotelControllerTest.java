package com.hotel_booking.controller;

import com.hotel_booking.dto.ExternalHotelOfferDto;
import com.hotel_booking.dto.HotelDto;
import com.hotel_booking.dto.RoomDto;
import com.hotel_booking.dto.request.LiteApiBookableRoomRequest;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.mapper.RoomMapper;
import com.hotel_booking.repository.HotelRepository;
import com.hotel_booking.repository.RoomRepository;
import com.hotel_booking.service.HotelService;
import com.hotel_booking.service.LiteApiHotelSearchService;
import org.mockito.ArgumentCaptor;
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

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HotelControllerTest {

    @Mock
    private HotelService hotelService;

    @Mock
    private LiteApiHotelSearchService liteApiHotelSearchService;

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMapper roomMapper;

    private HotelController controller;

    @BeforeEach
    void setUp() {
        controller = new HotelController(hotelService, liteApiHotelSearchService, hotelRepository, roomRepository, roomMapper);
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

    @Test
    void searchLiteApiReturnsOffersFromService() {
        LocalDate checkIn = LocalDate.of(2026, 6, 1);
        LocalDate checkOut = LocalDate.of(2026, 6, 5);
        List<ExternalHotelOfferDto> expected = List.of(ExternalHotelOfferDto.builder()
                .provider("LiteAPI")
                .hotelId("lp19d80")
                .name("LiteAPI Hotel")
                .cityCode("Paris, FR")
                .priceTotal("250.00")
                .currency("USD")
                .build());

        when(liteApiHotelSearchService.search("Paris", "FR", checkIn, checkOut, 2, 1, "USD", "US")).thenReturn(expected);

        List<ExternalHotelOfferDto> response = controller.searchLiteApi("Paris", "FR", checkIn, checkOut, 2, 1, "USD", "US");

        assertThat(response).isSameAs(expected);
        verify(liteApiHotelSearchService).search("Paris", "FR", checkIn, checkOut, 2, 1, "USD", "US");
    }

    @Test
    void createBookableRoomFromLiteApiCreatesHotelAndRoom() {
        LiteApiBookableRoomRequest request = liteApiRequest("300.50");
        Hotel savedHotel = Hotel.builder()
                .id(9L)
                .name("LiteAPI Hotel")
                .location("New York, US")
                .description("113 West 24th Street")
                .build();
        RoomDto expected = RoomDto.builder()
                .id(20L)
                .roomNumber(800123)
                .type("DOUBLE")
                .price(300.50)
                .available(true)
                .hotelId(9L)
                .build();

        when(hotelRepository.findFirstByNameAndLocation("LiteAPI Hotel", "New York, US")).thenReturn(Optional.empty());
        when(hotelRepository.save(any(Hotel.class))).thenReturn(savedHotel);
        when(roomRepository.findByHotelId(9L)).thenReturn(List.of());
        when(roomRepository.existsByRoomNumber(any(Integer.class))).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(20L);
            return room;
        });
        when(roomMapper.toDto(any(Room.class))).thenReturn(expected);

        RoomDto response = controller.createBookableRoomFromLiteApi(request);

        assertThat(response).isSameAs(expected);

        ArgumentCaptor<Hotel> hotelCaptor = ArgumentCaptor.forClass(Hotel.class);
        verify(hotelRepository).save(hotelCaptor.capture());
        assertThat(hotelCaptor.getValue())
                .extracting(Hotel::getName, Hotel::getLocation, Hotel::getDescription)
                .containsExactly("LiteAPI Hotel", "New York, US", "113 West 24th Street");

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getType().name()).isEqualTo("DOUBLE");
        assertThat(roomCaptor.getValue().getPricePerNight()).isEqualTo(300.50);
        assertThat(roomCaptor.getValue().isAvailable()).isTrue();
        assertThat(roomCaptor.getValue().getDescription()).contains("LiteAPI hotel id: hotel-1");
    }

    @Test
    void createBookableRoomFromLiteApiReusesMatchingRoom() {
        LiteApiBookableRoomRequest request = liteApiRequest("not-a-number");
        Hotel hotel = Hotel.builder()
                .id(9L)
                .name("LiteAPI Hotel")
                .location("New York, US")
                .description("LiteAPI hotel")
                .build();
        Room existingRoom = Room.builder()
                .id(20L)
                .roomNumber(800123)
                .pricePerNight(150.0)
                .description("Room Only | LiteAPI hotel id: hotel-1")
                .hotel(hotel)
                .build();
        RoomDto expected = RoomDto.builder().id(20L).price(150.0).hotelId(9L).build();

        when(hotelRepository.findFirstByNameAndLocation("LiteAPI Hotel", "New York, US")).thenReturn(Optional.of(hotel));
        when(roomRepository.findByHotelId(9L)).thenReturn(List.of(existingRoom));
        when(roomMapper.toDto(existingRoom)).thenReturn(expected);

        RoomDto response = controller.createBookableRoomFromLiteApi(request);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void createBookableRoomFromLiteApiSkipsRoomsWithMissingOrDifferentLiteApiId() {
        LiteApiBookableRoomRequest request = liteApiRequest("not-a-number");
        Hotel hotel = Hotel.builder()
                .id(9L)
                .name("LiteAPI Hotel")
                .location("New York, US")
                .description("LiteAPI hotel")
                .build();
        Room roomWithoutDescription = Room.builder()
                .id(20L)
                .description(null)
                .hotel(hotel)
                .build();
        Room roomForDifferentExternalHotel = Room.builder()
                .id(21L)
                .description("Room Only | LiteAPI hotel id: different-hotel")
                .hotel(hotel)
                .build();
        RoomDto expected = RoomDto.builder().id(22L).price(100.0).hotelId(9L).build();

        when(hotelRepository.findFirstByNameAndLocation("LiteAPI Hotel", "New York, US")).thenReturn(Optional.of(hotel));
        when(roomRepository.findByHotelId(9L)).thenReturn(List.of(roomWithoutDescription, roomForDifferentExternalHotel));
        when(roomRepository.existsByRoomNumber(any(Integer.class))).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(22L);
            return room;
        });
        when(roomMapper.toDto(any(Room.class))).thenReturn(expected);

        RoomDto response = controller.createBookableRoomFromLiteApi(request);

        assertThat(response).isSameAs(expected);

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getPricePerNight()).isEqualTo(100.0);
    }

    @Test
    void createBookableRoomFromLiteApiUsesDefaultsForBlankOptionalValues() {
        LiteApiBookableRoomRequest request = LiteApiBookableRoomRequest.builder()
                .hotelId(" ")
                .name("  LiteAPI Hotel  ")
                .location("  New York, US  ")
                .address(" ")
                .description(" ")
                .priceTotal(" ")
                .build();
        Hotel hotel = Hotel.builder()
                .id(9L)
                .name("LiteAPI Hotel")
                .location("New York, US")
                .description("LiteAPI hotel")
                .build();
        RoomDto expected = RoomDto.builder().id(20L).price(100.0).hotelId(9L).build();

        when(hotelRepository.findFirstByNameAndLocation("LiteAPI Hotel", "New York, US")).thenReturn(Optional.of(hotel));
        when(roomRepository.findByHotelId(9L)).thenReturn(List.of());
        when(roomRepository.existsByRoomNumber(any(Integer.class))).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(20L);
            return room;
        });
        when(roomMapper.toDto(any(Room.class))).thenReturn(expected);

        RoomDto response = controller.createBookableRoomFromLiteApi(request);

        assertThat(response).isSameAs(expected);

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getPricePerNight()).isEqualTo(100.0);
        assertThat(roomCaptor.getValue().getDescription()).isEqualTo("LiteAPI hotel | LiteAPI hotel id: unknown");
    }

    @Test
    void createBookableRoomFromLiteApiUsesDefaultPriceForInvalidPrice() {
        LiteApiBookableRoomRequest request = liteApiRequest("not-a-number");
        Hotel hotel = Hotel.builder()
                .id(9L)
                .name("LiteAPI Hotel")
                .location("New York, US")
                .description("LiteAPI hotel")
                .build();
        RoomDto expected = RoomDto.builder().id(20L).price(100.0).hotelId(9L).build();

        when(hotelRepository.findFirstByNameAndLocation("LiteAPI Hotel", "New York, US")).thenReturn(Optional.of(hotel));
        when(roomRepository.findByHotelId(9L)).thenReturn(List.of());
        when(roomRepository.existsByRoomNumber(any(Integer.class))).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(20L);
            return room;
        });
        when(roomMapper.toDto(any(Room.class))).thenReturn(expected);

        RoomDto response = controller.createBookableRoomFromLiteApi(request);

        assertThat(response).isSameAs(expected);

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getPricePerNight()).isEqualTo(100.0);
    }

    @Test
    void createBookableRoomFromLiteApiUsesDefaultPriceForMissingPrice() {
        LiteApiBookableRoomRequest request = liteApiRequest(null);
        Hotel hotel = Hotel.builder()
                .id(9L)
                .name("LiteAPI Hotel")
                .location("New York, US")
                .description("LiteAPI hotel")
                .build();
        RoomDto expected = RoomDto.builder().id(20L).price(100.0).hotelId(9L).build();

        when(hotelRepository.findFirstByNameAndLocation("LiteAPI Hotel", "New York, US")).thenReturn(Optional.of(hotel));
        when(roomRepository.findByHotelId(9L)).thenReturn(List.of());
        when(roomRepository.existsByRoomNumber(any(Integer.class))).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(20L);
            return room;
        });
        when(roomMapper.toDto(any(Room.class))).thenReturn(expected);

        RoomDto response = controller.createBookableRoomFromLiteApi(request);

        assertThat(response).isSameAs(expected);

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getPricePerNight()).isEqualTo(100.0);
    }

    @Test
    void createBookableRoomFromLiteApiIncrementsRoomNumberWhenGeneratedNumberExists() {
        LiteApiBookableRoomRequest request = liteApiRequest("300.50");
        Hotel hotel = Hotel.builder()
                .id(9L)
                .name("LiteAPI Hotel")
                .location("New York, US")
                .description("LiteAPI hotel")
                .build();
        RoomDto expected = RoomDto.builder().id(20L).hotelId(9L).build();

        when(hotelRepository.findFirstByNameAndLocation("LiteAPI Hotel", "New York, US")).thenReturn(Optional.of(hotel));
        when(roomRepository.findByHotelId(9L)).thenReturn(List.of());
        when(roomRepository.existsByRoomNumber(any(Integer.class))).thenReturn(true, false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(20L);
            return room;
        });
        when(roomMapper.toDto(any(Room.class))).thenReturn(expected);

        RoomDto response = controller.createBookableRoomFromLiteApi(request);

        assertThat(response).isSameAs(expected);

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getRoomNumber()).isBetween(800001, 900000);
    }

    @Test
    void createBookableRoomFromLiteApiRejectsMissingRequiredText() {
        assertThatThrownBy(() -> controller.createBookableRoomFromLiteApi(LiteApiBookableRoomRequest.builder()
                .location("New York, US")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hotel name is required");

        assertThatThrownBy(() -> controller.createBookableRoomFromLiteApi(LiteApiBookableRoomRequest.builder()
                .name("LiteAPI Hotel")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hotel location is required");

        assertThatThrownBy(() -> controller.createBookableRoomFromLiteApi(LiteApiBookableRoomRequest.builder()
                .name(" ")
                .location("New York, US")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hotel name is required");

        verify(hotelRepository, never()).save(any(Hotel.class));
    }

    @Test
    void firstTextReturnsEmptyStringWhenNoValueHasText() throws Exception {
        Method firstText = HotelController.class.getDeclaredMethod("firstText", String[].class);
        firstText.setAccessible(true);

        Object response = firstText.invoke(controller, (Object) new String[]{null, " ", ""});

        assertThat(response).isEqualTo("");
    }

    private HotelDto hotelDto(Long id, String name) {
        return HotelDto.builder()
                .id(id)
                .name(name)
                .location("Addis Ababa")
                .description("Central hotel")
                .build();
    }

    private LiteApiBookableRoomRequest liteApiRequest(String priceTotal) {
        return LiteApiBookableRoomRequest.builder()
                .hotelId("hotel-1")
                .name("LiteAPI Hotel")
                .location("New York, US")
                .address("113 West 24th Street")
                .priceTotal(priceTotal)
                .build();
    }
}
