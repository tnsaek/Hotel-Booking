package com.hotel_booking.mapper;

import com.hotel_booking.dto.request.BookingRequest;
import com.hotel_booking.dto.response.BookingResponse;
import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.BookingStatus;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {
    public Booking toEntity(BookingRequest dto, User user, Room room){
        return Booking.builder().user(user).room(room).checkInDate(dto.getCheckIn()).checkOutDate(dto.getCheckOut())
                .bookingStatus(BookingStatus.PENDING).totalPrice(room.getPricePerNight()).build();
    }

    public BookingResponse toDto(Booking booking){
        return BookingResponse.builder().bookingId(booking.getId()).status(booking.getBookingStatus().name())
                .totalAmount(booking.getTotalPrice()).checkIn(booking.getCheckInDate()).checkOut(booking.getCheckOutDate()).build();
    }
}
