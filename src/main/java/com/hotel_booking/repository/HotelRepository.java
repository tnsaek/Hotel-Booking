package com.hotel_booking.repository;

import com.hotel_booking.entity.Hotel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {
    java.util.Optional<Hotel> findFirstByNameAndLocation(String name, String location);
    Page<Hotel> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Hotel> findByLocationContainingIgnoreCase(String location, Pageable pageable);
    Page<Hotel> findByNameContainingIgnoreCaseAndLocationContainingIgnoreCase(String name, String location, Pageable pageable);
}
