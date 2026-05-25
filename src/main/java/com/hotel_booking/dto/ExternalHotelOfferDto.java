package com.hotel_booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalHotelOfferDto {
    private String provider;
    private String hotelId;
    private String name;
    private String cityCode;
    private String address;
    private Double latitude;
    private Double longitude;
    private String offerId;
    private String checkInDate;
    private String checkOutDate;
    private Integer adults;
    private Integer roomQuantity;
    private String roomType;
    private String description;
    private String priceTotal;
    private String currency;
}
