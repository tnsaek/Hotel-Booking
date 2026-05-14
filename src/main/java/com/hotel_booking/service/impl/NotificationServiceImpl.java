package com.hotel_booking.service.impl;

import com.hotel_booking.dto.NotificationDto;
import com.hotel_booking.entity.Notification;
import com.hotel_booking.entity.User;
import com.hotel_booking.entity.enums.UserRole;
import com.hotel_booking.exception.ResourceNotFoundException;
import com.hotel_booking.repository.NotificationRepository;
import com.hotel_booking.repository.UserRepository;
import com.hotel_booking.service.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    public void createBookingNotification(User user, String type, String message) {
        Notification notification = Notification.builder()
                .type(type)
                .body(message)
                .read(false)
                .user(user)
                .build();
        notificationRepository.save(notification);
    }

    @Override
    public List<NotificationDto> getUserNotifications(Long userId) {
        validateUserAccess(userId);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public long getUnreadCount(Long userId) {
        validateUserAccess(userId);
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationDto markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        validateUserAccess(notification.getUser().getId());
        notification.setRead(true);
        return toDto(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        validateUserAccess(userId);
        notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .forEach(notification -> notification.setRead(true));
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .message(notification.getBody())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private void validateUserAccess(Long userId) {
        User currentUser = getCurrentUser();
        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }
        if (!currentUser.getId().equals(userId)) {
            throw new IllegalStateException("You can only access your own notifications");
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
}
