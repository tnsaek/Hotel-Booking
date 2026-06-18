package com.hotel_booking.service;

import com.hotel_booking.entity.enums.ChatbotIntent;

public interface ChatbotIntentService {
    ChatbotIntent detectIntent(String message);
}
