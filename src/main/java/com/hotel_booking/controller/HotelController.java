package com.hotel_booking.controller;

import com.hotel_booking.dto.ExternalHotelOfferDto;
import com.hotel_booking.dto.HotelDto;
import com.hotel_booking.dto.RoomDto;
import com.hotel_booking.dto.request.LiteApiBookableRoomRequest;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.enums.RoomType;
import com.hotel_booking.mapper.RoomMapper;
import com.hotel_booking.repository.HotelRepository;
import com.hotel_booking.repository.RoomRepository;
import com.hotel_booking.service.HotelService;
import com.hotel_booking.service.LiteApiHotelSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class HotelController {

    private final HotelService hotelService;
    private final LiteApiHotelSearchService liteApiHotelSearchService;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<HotelDto> create(@RequestBody HotelDto hotelDto){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(hotelService.create(hotelDto));
    }

    @GetMapping("/liteapi/search")
    public List<ExternalHotelOfferDto> searchLiteApi(
            @RequestParam String cityName,
            @RequestParam String countryCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOutDate,
            @RequestParam(defaultValue = "1") Integer adults,
            @RequestParam(defaultValue = "1") Integer roomQuantity,
            @RequestParam(defaultValue = "USD") String currency,
            @RequestParam(defaultValue = "US") String guestNationality) {
        return liteApiHotelSearchService.search(
                cityName,
                countryCode,
                checkInDate,
                checkOutDate,
                adults,
                roomQuantity,
                currency,
                guestNationality
        );
    }

    @PostMapping("/liteapi/bookable-room")
    public RoomDto createBookableRoomFromLiteApi(@RequestBody LiteApiBookableRoomRequest request) {
        String hotelName = requireText(request.getName(), "Hotel name is required");
        String location = requireText(request.getLocation(), "Hotel location is required");
        String description = firstText(request.getDescription(), request.getAddress(), "LiteAPI hotel");

        Hotel hotel = hotelRepository.findFirstByNameAndLocation(hotelName, location)
                .orElseGet(() -> hotelRepository.save(Hotel.builder()
                        .name(hotelName)
                        .location(location)
                        .description(description)
                        .build()));

        Room room = roomRepository.findByHotelId(hotel.getId())
                .stream()
                .filter(existingRoom -> existingRoom.getDescription() != null
                        && existingRoom.getDescription().contains("LiteAPI hotel id: " + request.getHotelId()))
                .findFirst()
                .orElseGet(() -> roomRepository.save(Room.builder()
                        .roomNumber(nextExternalRoomNumber(request.getHotelId()))
                        .type(RoomType.DOUBLE)
                        .pricePerNight(parsePriceOrDefault(request.getPriceTotal()))
                        .available(true)
                        .description(description + " | LiteAPI hotel id: " + firstText(request.getHotelId(), "unknown"))
                        .hotel(hotel)
                        .build()));

        return roomMapper.toDto(room);
    }

    @GetMapping("/{id}")
    public HotelDto get(@PathVariable Long id){
        return hotelService.get(id);
    }

    @GetMapping
    public Page<HotelDto> getALL(Pageable pageable){
        return hotelService.getAll(pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public HotelDto update(@PathVariable Long id, @RequestBody HotelDto dto){
        return hotelService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        hotelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public Page<HotelDto> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String location,
            Pageable pageable){
        return hotelService.search(name, location, pageable);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private Double parsePriceOrDefault(String priceTotal) {
        if (priceTotal == null || priceTotal.isBlank()) {
            return 100.0;
        }
        try {
            return Double.parseDouble(priceTotal);
        } catch (NumberFormatException exception) {
            return 100.0;
        }
    }

    private Integer nextExternalRoomNumber(String hotelId) {
        int seed = Math.abs(firstText(hotelId, "liteapi").hashCode());
        int roomNumber = 800000 + seed % 100000;
        while (roomRepository.existsByRoomNumber(roomNumber)) {
            roomNumber++;
        }
        return roomNumber;
    }
}
