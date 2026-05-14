package com.hotel_booking.controller;

import com.hotel_booking.dto.HotelDto;
import com.hotel_booking.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class HotelController {

    private final HotelService hotelService;

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<HotelDto> create(@RequestBody HotelDto hotelDto){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(hotelService.create(hotelDto));
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
}
