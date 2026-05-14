package com.hotel_booking.entity;

import com.hotel_booking.entity.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    @Test
    void prePersistSetsCreatedAtWhenMissing() {
        Notification notification = Notification.builder()
                .type("BOOKING")
                .body("Booking created")
                .read(false)
                .user(user())
                .build();
        LocalDateTime beforePersist = LocalDateTime.now();

        notification.prePersist();

        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.getCreatedAt()).isAfterOrEqualTo(beforePersist);
        assertThat(notification.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void prePersistPreservesExistingCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 10, 15, 45);
        Notification notification = Notification.builder()
                .type("PAYMENT")
                .body("Payment received")
                .read(true)
                .createdAt(createdAt)
                .user(user())
                .build();

        notification.prePersist();

        assertThat(notification.getCreatedAt()).isSameAs(createdAt);
    }

    @Test
    void noArgsConstructorSettersAndGettersWork() {
        User user = user();
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 10, 16, 0);
        Notification notification = new Notification();

        notification.setId(10L);
        notification.setType("BOOKING");
        notification.setBody("Booking updated");
        notification.setRead(true);
        notification.setCreatedAt(createdAt);
        notification.setUser(user);

        assertThat(notification.getId()).isEqualTo(10L);
        assertThat(notification.getType()).isEqualTo("BOOKING");
        assertThat(notification.getBody()).isEqualTo("Booking updated");
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getCreatedAt()).isSameAs(createdAt);
        assertThat(notification.getUser()).isSameAs(user);
    }

    @Test
    void allArgsConstructorInitializesFields() {
        User user = user();
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 10, 16, 30);

        Notification notification = new Notification(10L, "BOOKING", "Booking updated", false, createdAt, user);

        assertThat(notification.getId()).isEqualTo(10L);
        assertThat(notification.getType()).isEqualTo("BOOKING");
        assertThat(notification.getBody()).isEqualTo("Booking updated");
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getCreatedAt()).isSameAs(createdAt);
        assertThat(notification.getUser()).isSameAs(user);
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
}
