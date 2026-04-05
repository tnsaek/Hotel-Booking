package com.hotel_booking.controller;

import com.hotel_booking.dto.HotelDto;
import com.hotel_booking.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @PostMapping
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
    public HotelDto update(@PathVariable Long id, @RequestBody HotelDto dto){
        return hotelService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        hotelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
