package com.hotel_booking.service.impl;

import com.hotel_booking.dto.NotificationDto;
import com.hotel_booking.entity.Notification;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.repository.NotificationRepository;
import com.hotel_booking.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String USER_EMAIL = "guest@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBookingNotificationSavesUnreadNotification() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);

        service.createBookingNotification(user, "BOOKING_CREATED", "Booking created");

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertThat(notification.getUser()).isSameAs(user);
        assertThat(notification.getType()).isEqualTo("BOOKING_CREATED");
        assertThat(notification.getBody()).isEqualTo("Booking created");
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void getUserNotificationsReturnsMappedNotificationsForOwner() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
        Notification notification = notification(10L, user, "BOOKING_CREATED", "Created", false, createdAt);

        authenticatedAs(user);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(notification));

        List<NotificationDto> response = service.getUserNotifications(user.getId());

        assertThat(response).hasSize(1);
        assertThat(response.get(0))
                .usingRecursiveComparison()
                .isEqualTo(NotificationDto.builder()
                        .id(10L)
                        .type("BOOKING_CREATED")
                        .message("Created")
                        .read(false)
                        .createdAt(createdAt)
                        .build());
    }

    @Test
    void getUserNotificationsAllowsAdminForAnyUser() {
        User admin = user(99L, ADMIN_EMAIL, UserRole.ADMIN);
        User owner = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Notification notification = notification(10L, owner, "BOOKING_UPDATED", "Updated", true, LocalDateTime.now());

        authenticatedAs(admin);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(owner.getId())).thenReturn(List.of(notification));

        List<NotificationDto> response = service.getUserNotifications(owner.getId());

        assertThat(response).extracting(NotificationDto::getId).containsExactly(10L);
    }

    @Test
    void getUserNotificationsThrowsWhenCurrentUserIsDifferentFromRequestedUser() {
        User currentUser = user(2L, USER_EMAIL, UserRole.CUSTOMER);
        authenticatedAs(currentUser);

        assertThatThrownBy(() -> service.getUserNotifications(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You can only access your own notifications");
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getUnreadCountReturnsCountForOwner() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        authenticatedAs(user);
        when(notificationRepository.countByUserIdAndReadFalse(user.getId())).thenReturn(3L);

        long count = service.getUnreadCount(user.getId());

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void markAsReadMarksNotificationAndReturnsDto() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        LocalDateTime createdAt = LocalDateTime.now();
        Notification notification = notification(10L, user, "BOOKING_CREATED", "Created", false, createdAt);

        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        authenticatedAs(user);

        NotificationDto response = service.markAsRead(notification.getId());

        assertThat(notification.isRead()).isTrue();
        assertThat(response.getId()).isEqualTo(notification.getId());
        assertThat(response.isRead()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Created");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void markAsReadThrowsWhenNotificationDoesNotExist() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Notification not found");
    }

    @Test
    void markAllAsReadMarksAllNotificationsForOwner() {
        User user = user(1L, USER_EMAIL, UserRole.CUSTOMER);
        Notification first = notification(10L, user, "BOOKING_CREATED", "Created", false, LocalDateTime.now());
        Notification second = notification(11L, user, "BOOKING_UPDATED", "Updated", false, LocalDateTime.now());

        authenticatedAs(user);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(first, second));

        service.markAllAsRead(user.getId());

        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
    }

    @Test
    void accessValidationThrowsWhenAuthenticationIsMissing() {
        assertThatThrownBy(() -> service.getUnreadCount(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User is not authenticated");
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void accessValidationThrowsWhenAuthenticationNameIsMissing() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> service.getUnreadCount(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User is not authenticated");
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void accessValidationThrowsWhenAuthenticatedUserDoesNotExist() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(USER_EMAIL, "password"));
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUnreadCount(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
        verify(notificationRepository, never()).countByUserIdAndReadFalse(1L);
    }

    private void authenticatedAs(User currentUser) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(currentUser.getEmail(), "password"));
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
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

    private Notification notification(
            Long id,
            User user,
            String type,
            String body,
            boolean read,
            LocalDateTime createdAt
    ) {
        return Notification.builder()
                .id(id)
                .user(user)
                .type(type)
                .body(body)
                .read(read)
                .createdAt(createdAt)
                .build();
    }
}
