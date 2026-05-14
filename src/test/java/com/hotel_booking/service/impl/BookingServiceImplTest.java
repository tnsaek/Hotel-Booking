package com.hotel_booking.service.impl;

import com.hotel_booking.dto.request.BookingRequest;
import com.hotel_booking.dto.request.BookingUpdateRequest;
import com.hotel_booking.dto.response.BookingResponse;
import com.hotel_booking.dto.response.PaymentResponse;
import com.hotel_booking.entity.Booking;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.BookingStatus;
import com.hotel_booking.entity.enums.RoomType;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.mapper.BookingMapper;
import com.hotel_booking.repository.BookingRepository;
import com.hotel_booking.repository.RoomRepository;
import com.hotel_booking.repository.UserRepository;
import com.hotel_booking.service.NotificationService;
import com.hotel_booking.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final String USER_EMAIL = "guest@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private PaymentService paymentService;
    @Mock
    private NotificationService notificationService;

    private BookingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(
                bookingRepository,
                userRepository,
                roomRepository,
                new BookingMapper(),
                paymentService,
                notificationService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBookingSavesBookingWithCalculatedTotalAndNotification() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Room room = room(10L, true, 125.0);
        BookingRequest request = bookingRequest(user.getId(), room.getId(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(8));

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        authenticatedAs(user);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlappingBookings(eq(room.getId()), eq(request.getCheckIn()), eq(request.getCheckOut()), anyCollection()))
                .thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(55L);
            return booking;
        });

        BookingResponse response = service.createBooking(request);

        assertThat(response.getBookingId()).isEqualTo(55L);
        assertThat(response.getTotalAmount()).isEqualTo(375.0);
        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING.name());
        verify(notificationService).createBookingNotification(
                eq(user),
                eq("BOOKING_CREATED"),
                eq("Your booking #55 was created for Test Hotel.")
        );
    }

    @Test
    void createBookingThrowsWhenRequestedUserDoesNotExist() {
        BookingRequest request = bookingRequest(1L, 10L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));
        when(userRepository.findById(request.getUserId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void createBookingThrowsWhenCurrentUserIsDifferentFromRequestedUser() {
        User requestedUser = user(1L, "requested@example.com", UserRole.CUSTOMER);
        User currentUser = user(2L, USER_EMAIL, UserRole.CUSTOMER);
        BookingRequest request = bookingRequest(requestedUser.getId(), 10L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));

        when(userRepository.findById(requestedUser.getId())).thenReturn(Optional.of(requestedUser));
        authenticatedAs(currentUser);

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You can only access your own bookings");
        verifyNoInteractions(roomRepository);
    }

    @Test
    void createBookingThrowsWhenRoomDoesNotExist() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        BookingRequest request = bookingRequest(user.getId(), 10L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        authenticatedAs(user);
        when(roomRepository.findById(request.getRoomId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Room not found");
    }

    @Test
    void createBookingThrowsWhenRoomIsUnavailable() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Room room = room(10L, false, 100.0);
        BookingRequest request = bookingRequest(user.getId(), room.getId(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        authenticatedAs(user);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Room not available");
    }

    @Test
    void createBookingThrowsWhenCheckoutIsNotAfterCheckin() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Room room = room(10L, true, 100.0);
        LocalDate checkIn = LocalDate.now().plusDays(5);
        BookingRequest request = bookingRequest(user.getId(), room.getId(), checkIn, checkIn);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        authenticatedAs(user);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Check-out date must be after check-in date");
    }

    @Test
    void createBookingThrowsWhenRoomHasOverlappingActiveBooking() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Room room = room(10L, true, 100.0);
        BookingRequest request = bookingRequest(user.getId(), room.getId(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7));
        Booking conflict = booking(99L, user, room, request.getCheckIn(), request.getCheckOut(), BookingStatus.CONFIRMED, 200.0);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        authenticatedAs(user);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlappingBookings(eq(room.getId()), eq(request.getCheckIn()), eq(request.getCheckOut()), anyCollection()))
                .thenReturn(List.of(conflict));

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("This room is already booked from " + request.getCheckIn() + " to " + request.getCheckOut());
    }

    @Test
    void getBookingAllowsAdminToAccessAnyBooking() {
        User owner = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        User admin = user(9L, ADMIN_EMAIL, UserRole.ADMIN);
        Booking booking = booking(20L, owner, room(10L, true, 100.0), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), BookingStatus.CONFIRMED, 200.0);

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(admin);

        BookingResponse response = service.getBooking(booking.getId());

        assertThat(response.getBookingId()).isEqualTo(booking.getId());
        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED.name());
    }

    @Test
    void getBookingThrowsWhenBookingDoesNotExist() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBooking(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Booking not found");
    }

    @Test
    void getBookingThrowsWhenAuthenticationIsMissing() {
        User owner = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Booking booking = booking(20L, owner, room(10L, true, 100.0), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), BookingStatus.CONFIRMED, 200.0);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.getBooking(booking.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User is not authenticated");
    }

    @Test
    void getUserBookingsThrowsWhenAuthenticationNameIsMissing() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> service.getUserBookings(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User is not authenticated");
    }

    @Test
    void getUserBookingsThrowsWhenAuthenticatedUserDoesNotExist() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(USER_EMAIL, "password"));
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserBookings(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void getUserBookingsReturnsMappedBookingsForOwner() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Room room = room(10L, true, 80.0);
        Booking first = booking(1L, user, room, LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), BookingStatus.PENDING, 80.0);
        Booking second = booking(2L, user, room, LocalDate.now().plusDays(7), LocalDate.now().plusDays(9), BookingStatus.CONFIRMED, 160.0);

        authenticatedAs(user);
        when(bookingRepository.findByUserId(user.getId())).thenReturn(List.of(first, second));

        List<BookingResponse> responses = service.getUserBookings(user.getId());

        assertThat(responses).extracting(BookingResponse::getBookingId).containsExactly(1L, 2L);
    }

    @Test
    void updateBookingReturnsPaymentRequiredResponseWithoutChangingBookingWhenCheckoutIsNeeded() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Room room = room(10L, true, 100.0);
        LocalDate originalCheckIn = LocalDate.now().plusDays(5);
        LocalDate originalCheckOut = LocalDate.now().plusDays(7);
        Booking booking = booking(20L, user, room, originalCheckIn, originalCheckOut, BookingStatus.CONFIRMED, 200.0);
        BookingUpdateRequest request = updateRequest(originalCheckIn, originalCheckOut.plusDays(2));

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(user);
        when(bookingRepository.findOverlappingBookings(eq(room.getId()), eq(request.getCheckIn()), eq(request.getCheckOut()), anyCollection()))
                .thenReturn(List.of());
        when(paymentService.reconcilePaymentForUpdatedBooking(booking, 200.0, 400.0, request.getCheckIn(), request.getCheckOut()))
                .thenReturn(PaymentResponse.builder().checkoutUrl("https://checkout.example/session").build());

        BookingResponse response = service.updateBooking(booking.getId(), request);

        assertThat(response.getPaymentRequired()).isTrue();
        assertThat(response.getAdditionalAmount()).isEqualTo(200.0);
        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.example/session");
        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED.name());
        assertThat(response.getTotalAmount()).isEqualTo(200.0);
        assertThat(response.getCheckIn()).isEqualTo(originalCheckIn);
        assertThat(response.getCheckOut()).isEqualTo(originalCheckOut);
        verify(notificationService, never()).createBookingNotification(any(), any(), any());
    }

    @Test
    void updateBookingAppliesChangesWhenNoAdditionalPaymentIsRequired() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Room room = room(10L, true, 75.0);
        Booking booking = booking(20L, user, room, LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), BookingStatus.PENDING, 225.0);
        BookingUpdateRequest request = updateRequest(LocalDate.now().plusDays(6), LocalDate.now().plusDays(8));

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(user);
        when(bookingRepository.findOverlappingBookings(eq(room.getId()), eq(request.getCheckIn()), eq(request.getCheckOut()), anyCollection()))
                .thenReturn(List.of(booking));
        when(paymentService.reconcilePaymentForUpdatedBooking(booking, 225.0, 150.0, request.getCheckIn(), request.getCheckOut()))
                .thenReturn(null);

        BookingResponse response = service.updateBooking(booking.getId(), request);

        assertThat(response.getCheckIn()).isEqualTo(request.getCheckIn());
        assertThat(response.getCheckOut()).isEqualTo(request.getCheckOut());
        assertThat(response.getTotalAmount()).isEqualTo(150.0);
        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING.name());
        verify(notificationService).createBookingNotification(user, "BOOKING_UPDATED", "Your booking #20 was updated.");
    }

    @Test
    void updateBookingAppliesChangesWhenPaymentResponseHasNoCheckoutUrl() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Room room = room(10L, true, 50.0);
        Booking booking = booking(20L, user, room, LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), BookingStatus.CONFIRMED, 50.0);
        BookingUpdateRequest request = updateRequest(LocalDate.now().plusDays(5), LocalDate.now().plusDays(7));

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(user);
        when(bookingRepository.findOverlappingBookings(eq(room.getId()), eq(request.getCheckIn()), eq(request.getCheckOut()), anyCollection()))
                .thenReturn(List.of(booking));
        when(paymentService.reconcilePaymentForUpdatedBooking(booking, 50.0, 100.0, request.getCheckIn(), request.getCheckOut()))
                .thenReturn(PaymentResponse.builder().checkoutUrl(null).build());

        BookingResponse response = service.updateBooking(booking.getId(), request);

        assertThat(response.getTotalAmount()).isEqualTo(100.0);
        assertThat(response.getPaymentRequired()).isNull();
        verify(notificationService).createBookingNotification(user, "BOOKING_UPDATED", "Your booking #20 was updated.");
    }

    @Test
    void updateBookingThrowsWhenBookingDoesNotExist() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBooking(404L, updateRequest(LocalDate.now().plusDays(5), LocalDate.now().plusDays(6))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Booking not found");
    }

    @Test
    void updateBookingThrowsWhenBookingIsCancelled() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Booking booking = booking(20L, user, room(10L, true, 100.0), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), BookingStatus.CANCELLED, 200.0);

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(user);

        assertThatThrownBy(() -> service.updateBooking(booking.getId(), updateRequest(LocalDate.now().plusDays(5), LocalDate.now().plusDays(8))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cancelled bookings can not be modified");
    }

    @Test
    void updateBookingThrowsInsideModificationWindow() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Booking booking = booking(20L, user, room(10L, true, 100.0), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), BookingStatus.CONFIRMED, 200.0);

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(user);

        assertThatThrownBy(() -> service.updateBooking(booking.getId(), updateRequest(LocalDate.now().plusDays(1), LocalDate.now().plusDays(4))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bookings can only be updated or cancelled more than 24 hours before check-in");
    }

    @Test
    void updateBookingThrowsWhenNewDatesOverlapAnotherBooking() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Room room = room(10L, true, 100.0);
        Booking booking = booking(20L, user, room, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), BookingStatus.CONFIRMED, 200.0);
        Booking conflict = booking(21L, user, room, LocalDate.now().plusDays(8), LocalDate.now().plusDays(10), BookingStatus.CONFIRMED, 200.0);
        BookingUpdateRequest request = updateRequest(LocalDate.now().plusDays(8), LocalDate.now().plusDays(10));

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(user);
        when(bookingRepository.findOverlappingBookings(eq(room.getId()), eq(request.getCheckIn()), eq(request.getCheckOut()), anyCollection()))
                .thenReturn(List.of(booking, conflict));

        assertThatThrownBy(() -> service.updateBooking(booking.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("This room is already booked from " + conflict.getCheckInDate() + " to " + conflict.getCheckOutDate());
        verifyNoInteractions(paymentService);
    }

    @Test
    void cancelBookingRefundsCancelsAndCreatesNotification() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Booking booking = booking(20L, user, room(10L, true, 100.0), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), BookingStatus.CONFIRMED, 200.0);

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(user);

        service.cancelBooking(booking.getId());

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(paymentService).refundPaymentForCancelledBooking(booking);
        verify(notificationService).createBookingNotification(user, "BOOKING_CANCELLED", "Your booking #20 was cancelled.");
    }

    @Test
    void cancelBookingThrowsWhenBookingDoesNotExist() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelBooking(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Booking not found");
    }

    @Test
    void cancelBookingStopsBeforeRefundWhenModificationWindowFails() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Booking booking = booking(20L, user, room(10L, true, 100.0), LocalDate.now(), LocalDate.now().plusDays(1), BookingStatus.CONFIRMED, 100.0);

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(user);

        assertThatThrownBy(() -> service.cancelBooking(booking.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bookings can only be updated or cancelled more than 24 hours before check-in");
        verify(paymentService, never()).refundPaymentForCancelledBooking(any());
    }

    @Test
    void cancelBookingStopsBeforeRefundWhenBookingIsCancelled() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Booking booking = booking(20L, user, room(10L, true, 100.0), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), BookingStatus.CANCELLED, 200.0);

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        authenticatedAs(user);

        assertThatThrownBy(() -> service.cancelBooking(booking.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cancelled bookings can not be modified");
        verify(paymentService, never()).refundPaymentForCancelledBooking(any());
    }

    private void authenticatedAs(User currentUser) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(currentUser.getEmail(), "password"));
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
    }

    private BookingRequest bookingRequest(Long userId, Long roomId, LocalDate checkIn, LocalDate checkOut) {
        return BookingRequest.builder()
                .userId(userId)
                .roomId(roomId)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .build();
    }

    private BookingUpdateRequest updateRequest(LocalDate checkIn, LocalDate checkOut) {
        return BookingUpdateRequest.builder()
                .checkIn(checkIn)
                .checkOut(checkOut)
                .build();
    }

    private User user(Long id, String email, UserRole role) {
        return User.builder()
                .id(id)
                .name("Test User")
                .email(email)
                .phoneNumber("555-0100")
                .role(role)
                .active(true)
                .build();
    }

    private Room room(Long id, boolean available, double pricePerNight) {
        return Room.builder()
                .id(id)
                .roomNumber(101)
                .type(RoomType.SUITE)
                .pricePerNight(pricePerNight)
                .available(available)
                .hotel(Hotel.builder().id(5L).name("Test Hotel").location("Test City").build())
                .build();
    }

    private Booking booking(
            Long id,
            User user,
            Room room,
            LocalDate checkIn,
            LocalDate checkOut,
            BookingStatus status,
            double totalPrice
    ) {
        return Booking.builder()
                .id(id)
                .user(user)
                .room(room)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .bookingStatus(status)
                .totalPrice(totalPrice)
                .build();
    }
}

