package com.hotel_booking.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiteApiBookableRoomRequest {
    private String hotelId;
    private String name;
    private String location;
    private String address;
    private String description;
    private String priceTotal;
}
