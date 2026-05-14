package com.hotel_booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private String status;
    private Double totalAmount;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Long roomId;
    private Integer roomNumber;
    private String roomType;
    private String hotelName;
    private Boolean paymentRequired;
    private Double additionalAmount;
    private String checkoutUrl;
}
