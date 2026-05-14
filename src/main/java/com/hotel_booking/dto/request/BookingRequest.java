package com.hotel_booking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {
    @NotNull
    private Long userId;
    @NotNull
    private Long roomId;
    @NotNull
    private LocalDate checkIn;
    @NotNull
    private LocalDate checkOut;
}
