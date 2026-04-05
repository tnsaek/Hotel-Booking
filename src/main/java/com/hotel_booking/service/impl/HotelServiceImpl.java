package com.hotel_booking.service.impl;

import com.hotel_booking.dto.HotelDto;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.mapper.HotelMapper;
import com.hotel_booking.repository.HotelRepository;
import com.hotel_booking.service.HotelService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;

    @Override
    @Transactional
    public HotelDto create(HotelDto dto) {
        Hotel hotel = hotelMapper.toEntity(dto);
        return hotelMapper.toDto(hotelRepository.save(hotel));
    }

    @Override
    public HotelDto get(Long id) {
        return hotelRepository.findById(id)
                .map(hotelMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));
    }

    @Override
    public Page<HotelDto> getAll(Pageable pageable) {
        return hotelRepository.findAll(pageable)
                .map(hotelMapper::toDto);
    }

    @Override
    @Transactional
    public HotelDto update(Long id, HotelDto dto) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));
        hotel.setName(dto.getName());
        hotel.setLocation(dto.getLocation());
        hotel.setDescription(dto.getDescription());

        return hotelMapper.toDto(hotel);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if(!hotelRepository.existsById(id)){
            throw new ResourceNotFoundException("Hotel not found");
        }
        hotelRepository.deleteById(id);
    }
}
