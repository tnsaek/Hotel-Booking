package com.hotel_booking.service.impl;

import com.hotel_booking.dto.request.BookingRequest;
import com.hotel_booking.dto.request.BookingUpdateRequest;
import com.hotel_booking.dto.response.BookingResponse;
import com.hotel_booking.dto.response.PaymentResponse;
import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.BookingStatus;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.mapper.BookingMapper;
import com.hotel_booking.repository.BookingRepository;
import com.hotel_booking.repository.RoomRepository;
import com.hotel_booking.repository.UserRepository;
import com.hotel_booking.service.BookingService;
import com.hotel_booking.service.NotificationService;
import com.hotel_booking.service.PaymentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BookingMapper bookingMapper;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private static final EnumSet<BookingStatus> ACTIVE_BOOKING_STATUSES =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);



    @Override
    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        validateUserAccess(user.getId());

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        if(!room.isAvailable()){
            throw new IllegalStateException("Room not available");
        }

        validateDateRange(request.getCheckIn(), request.getCheckOut());
        validateRoomAvailableForDates(room.getId(), request.getCheckIn(), request.getCheckOut(), null);

        Booking booking = bookingMapper.toEntity(request, user, room);
        booking.setTotalPrice(calculateTotalPrice(room, request.getCheckIn(), request.getCheckOut()));
        Booking savedBooking = bookingRepository.save(booking);
        notificationService.createBookingNotification(
                user,
                "BOOKING_CREATED",
                "Your booking #" + savedBooking.getId() + " was created for " + room.getHotel().getName() + "."
        );
        return bookingMapper.toDto(savedBooking);
    }

    @Override
    public BookingResponse getBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        validateUserAccess(booking.getUser().getId());
        return bookingMapper.toDto(booking);
    }

    @Override
    @Transactional
    public BookingResponse updateBooking(Long id, BookingUpdateRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        validateUserAccess(booking.getUser().getId());
        validateBookingModificationWindow(booking);
        validateDateRange(request.getCheckIn(), request.getCheckOut());
        validateRoomAvailableForDates(booking.getRoom().getId(), request.getCheckIn(), request.getCheckOut(), booking.getId());

        double oldTotal = booking.getTotalPrice();
        double newTotal = calculateTotalPrice(booking.getRoom(), request.getCheckIn(), request.getCheckOut());
        BookingStatus originalStatus = booking.getBookingStatus();

        PaymentResponse paymentResponse = paymentService.reconcilePaymentForUpdatedBooking(
                booking,
                oldTotal,
                newTotal,
                request.getCheckIn(),
                request.getCheckOut()
        );

        if (paymentResponse != null && paymentResponse.getCheckoutUrl() != null) {
            BookingResponse response = bookingMapper.toDto(booking);
            response.setPaymentRequired(true);
            response.setAdditionalAmount(newTotal - oldTotal);
            response.setCheckoutUrl(paymentResponse.getCheckoutUrl());
            response.setStatus(originalStatus.name());
            response.setTotalAmount(oldTotal);
            response.setCheckIn(booking.getCheckInDate());
            response.setCheckOut(booking.getCheckOutDate());
            return response;
        }

        booking.setCheckInDate(request.getCheckIn());
        booking.setCheckOutDate(request.getCheckOut());
        booking.setTotalPrice(newTotal);
        booking.setBookingStatus(originalStatus);
        notificationService.createBookingNotification(
                booking.getUser(),
                "BOOKING_UPDATED",
                "Your booking #" + booking.getId() + " was updated."
        );
        BookingResponse response = bookingMapper.toDto(booking);
        return response;
    }

    @Override
    @Transactional
    public void cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        validateUserAccess(booking.getUser().getId());
        validateBookingModificationWindow(booking);
        paymentService.refundPaymentForCancelledBooking(booking);
        booking.setBookingStatus(BookingStatus.CANCELLED);
        notificationService.createBookingNotification(
                booking.getUser(),
                "BOOKING_CANCELLED",
                "Your booking #" + booking.getId() + " was cancelled."
        );
    }

    @Override
    public List<BookingResponse> getUserBookings(Long userId) {
        validateUserAccess(userId);
        return bookingRepository.findByUserId(userId)
                .stream()
                .map(bookingMapper::toDto)
                .toList();
    }

    private void validateUserAccess(Long userId) {
        User currentUser = getCurrentUser();
        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }
        if (!currentUser.getId().equals(userId)) {
            throw new IllegalStateException("You can only access your own bookings");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("User is not authenticated");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void validateDateRange(LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }
    }

    private void validateRoomAvailableForDates(
            Long roomId,
            LocalDate requestedCheckIn,
            LocalDate requestedCheckOut,
            Long bookingIdToIgnore
    ) {
        List<Booking> overlappingBookings = bookingRepository
                .findOverlappingBookings(roomId, requestedCheckIn, requestedCheckOut, ACTIVE_BOOKING_STATUSES)
                .stream()
                .filter(booking -> bookingIdToIgnore == null || !booking.getId().equals(bookingIdToIgnore))
                .toList();

        if (overlappingBookings.isEmpty()) {
            return;
        }

        Booking firstConflict = overlappingBookings.get(0);
        throw new IllegalStateException(
                "This room is already booked from "
                        + firstConflict.getCheckInDate()
                        + " to "
                        + firstConflict.getCheckOutDate()
                        + ". Please book another room or choose different dates."
        );
    }

    private double calculateTotalPrice(Room room, LocalDate checkIn, LocalDate checkOut) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return room.getPricePerNight() * nights;
    }

    private void validateBookingModificationWindow(Booking booking) {
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled bookings can not be modified");
        }

        LocalDateTime modificationDeadline = booking.getCheckInDate().atStartOfDay().minusHours(24);
        if (!LocalDateTime.now().isBefore(modificationDeadline)) {
            throw new IllegalStateException("Bookings can only be updated or cancelled more than 24 hours before check-in");
        }
    }
}
