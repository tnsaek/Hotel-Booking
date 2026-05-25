package com.hotel_booking.repository;

import com.hotel_booking.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByHotelId(Long hotelId);
    List<Room> findByAvailableTrue();
    boolean existsByRoomNumber(Integer roomNumber);
}
