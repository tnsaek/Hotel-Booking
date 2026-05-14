package com.hotel_booking.mapper;

import com.hotel_booking.dto.request.BookingRequest;
import com.hotel_booking.dto.response.BookingResponse;
import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.BookingStatus;
import com.hotel_booking.entity.enums.RoomType;
import com.hotel_booking.entity.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BookingMapperTest {

    private BookingMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BookingMapper();
    }

    @Test
    void toEntityMapsRequestUserAndRoomToPendingBooking() {
        User user = user();
        Room room = room();
        BookingRequest request = BookingRequest.builder()
                .userId(user.getId())
                .roomId(room.getId())
                .checkIn(LocalDate.of(2026, 6, 1))
                .checkOut(LocalDate.of(2026, 6, 3))
                .build();

        Booking booking = mapper.toEntity(request, user, room);

        assertThat(booking.getUser()).isSameAs(user);
        assertThat(booking.getRoom()).isSameAs(room);
        assertThat(booking.getCheckInDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(booking.getCheckOutDate()).isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getTotalPrice()).isEqualTo(125.0);
    }

    @Test
    void toDtoMapsBookingAndNestedRoomHotelFields() {
        Booking booking = Booking.builder()
                .id(10L)
                .user(user())
                .room(room())
                .checkInDate(LocalDate.of(2026, 7, 1))
                .checkOutDate(LocalDate.of(2026, 7, 4))
                .bookingStatus(BookingStatus.CONFIRMED)
                .totalPrice(375.0)
                .build();

        BookingResponse response = mapper.toDto(booking);

        assertThat(response.getBookingId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getTotalAmount()).isEqualTo(375.0);
        assertThat(response.getCheckIn()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.getCheckOut()).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(response.getRoomId()).isEqualTo(5L);
        assertThat(response.getRoomNumber()).isEqualTo(101);
        assertThat(response.getRoomType()).isEqualTo("SUITE");
        assertThat(response.getHotelName()).isEqualTo("Grand Hotel");
        assertThat(response.getPaymentRequired()).isNull();
        assertThat(response.getAdditionalAmount()).isNull();
        assertThat(response.getCheckoutUrl()).isNull();
    }

    private User user() {
        return User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane.doe@example.com")
                .phoneNumber("555-0101")
                .password("secret")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
    }

    private Room room() {
        return Room.builder()
                .id(5L)
                .roomNumber(101)
                .type(RoomType.SUITE)
                .pricePerNight(125.0)
                .available(true)
                .description("City view")
                .hotel(Hotel.builder()
                        .id(2L)
                        .name("Grand Hotel")
                        .location("Addis Ababa")
                        .description("Central hotel")
                        .build())
                .build();
    }
}
