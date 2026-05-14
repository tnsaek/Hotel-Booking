package com.hotel_booking.service;

import com.hotel_booking.dto.NotificationDto;
import com.hotel_booking.entity.User;

import java.util.List;

public interface NotificationService {
    void createBookingNotification(User user, String type, String message);

    List<NotificationDto> getUserNotifications(Long userId);

    long getUnreadCount(Long userId);

    NotificationDto markAsRead(Long notificationId);

    void markAllAsRead(Long userId);
}
