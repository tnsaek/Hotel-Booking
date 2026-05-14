package com.hotel_booking.entity;

import com.hotel_booking.entity.enums.RoomType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private Integer roomNumber;
    @Enumerated(EnumType.STRING)
    private RoomType type;
    private Double pricePerNight;
    private boolean available;
    private String description;
    @ManyToOne
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;
    @OneToMany(mappedBy = "room")
    private List<Booking> bookings;
}