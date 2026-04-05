package com.hotel_booking.controller;

import com.hotel_booking.dto.RoomDto;
import com.hotel_booking.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomDto> create(@RequestBody RoomDto roomDto){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.create(roomDto));
    }

    @GetMapping("/{id}")
    public RoomDto get(@PathVariable Long id){
        return roomService.get(id);
    }

    @GetMapping("/hotel/{hotelId}")
    public List<RoomDto> getByHotel(@PathVariable Long id){
        return roomService.getByHotel(id);
    }

    @PutMapping("/{id}")
    public RoomDto update(@PathVariable Long id, @RequestBody RoomDto dto){
        return roomService.update(id, dto);
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
