package com.hotel_booking.service.impl;

import com.hotel_booking.dto.BookingRequest;
import com.hotel_booking.dto.BookingResponse;
import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.BookingStatus;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.mapper.BookingMapper;
import com.hotel_booking.repository.BookingRepository;
import com.hotel_booking.repository.RoomRepository;
import com.hotel_booking.repository.UserRepository;
import com.hotel_booking.service.BookingService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BookingMapper bookingMapper;



    @Override
    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        if(!room.isAvailable()){
            throw new IllegalStateException("Room not available");
        }

        Booking booking = bookingMapper.toEntity(request, user, room);
        return bookingMapper.toDto(bookingRepository.save(booking));
    }

    @Override
    public BookingResponse getBooking(Long id) {
        return bookingRepository.findById(id)
                .map(bookingMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    @Override
    @Transactional
    public void cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        booking.setBookingStatus(BookingStatus.CANCELLED);
    }
}
