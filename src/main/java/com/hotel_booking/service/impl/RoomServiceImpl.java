package com.hotel_booking.service.impl;

import com.hotel_booking.dto.RoomDto;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.enums.RoomType;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.mapper.RoomMapper;
import com.hotel_booking.repository.HotelRepository;
import com.hotel_booking.repository.RoomRepository;
import com.hotel_booking.service.RoomService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional
    public RoomDto create(RoomDto dto) {
        Hotel hotel = hotelRepository.findById(dto.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));
        Room room = roomMapper.toEntity(dto, hotel);
        return roomMapper.toDto(roomRepository.save(room));
    }

    @Override
    public RoomDto get(Long id) {
        return roomRepository.findById(id)
                .map(roomMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
    }

    @Override
    public List<RoomDto> getByHotel(Long hotelId) {
        return roomRepository.findByHotelId(hotelId)
                .stream()
                .map(roomMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public RoomDto update(Long id, RoomDto dto) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        room.setType(RoomType.valueOf(dto.getType()));
        room.setPricePerNight(dto.getPrice());
        room.setAvailable(dto.isAvailable());
        return roomMapper.toDto(room);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(!roomRepository.existsById(id)){
            throw new ResourceNotFoundException("Room not found");
        }
        roomRepository.deleteById(id);
    }
}
