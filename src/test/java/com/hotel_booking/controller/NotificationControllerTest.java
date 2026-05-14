package com.hotel_booking.controller;

import com.hotel_booking.dto.NotificationDto;
import com.hotel_booking.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
    }

    @Test
    void getUserNotificationsReturnsNotificationsFromService() {
        List<NotificationDto> expected = List.of(
                notificationDto(1L, false),
                notificationDto(2L, true)
        );

        when(notificationService.getUserNotifications(7L)).thenReturn(expected);

        ResponseEntity<List<NotificationDto>> response = controller.getUserNotifications(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(notificationService).getUserNotifications(7L);
    }

    @Test
    void getUnreadCountReturnsCountMapFromService() {
        when(notificationService.getUnreadCount(7L)).thenReturn(3L);

        ResponseEntity<Map<String, Long>> response = controller.getUnreadCount(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(Map.entry("count", 3L));
        verify(notificationService).getUnreadCount(7L);
    }

    @Test
    void markAsReadReturnsNotificationFromService() {
        NotificationDto expected = notificationDto(5L, true);

        when(notificationService.markAsRead(5L)).thenReturn(expected);

        ResponseEntity<NotificationDto> response = controller.markAsRead(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(notificationService).markAsRead(5L);
    }

    @Test
    void markAllAsReadCallsServiceAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.markAllAsRead(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(notificationService).markAllAsRead(7L);
    }

    private NotificationDto notificationDto(Long id, boolean read) {
        return NotificationDto.builder()
                .id(id)
                .type("BOOKING")
                .message("Booking update")
                .read(read)
                .createdAt(LocalDateTime.of(2026, 5, 10, 12, 0))
                .build();
    }
}
