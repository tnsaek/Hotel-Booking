package com.hotel_booking.repository;

import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);

    @Query("""
            select booking from Booking booking
            where booking.room.id = :roomId
              and booking.bookingStatus in :statuses
              and booking.checkInDate < :checkOut
              and booking.checkOutDate > :checkIn
            order by booking.checkInDate asc
            """)
    List<Booking> findOverlappingBookings(
            @Param("roomId") Long roomId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("statuses") Collection<BookingStatus> statuses
    );
}
