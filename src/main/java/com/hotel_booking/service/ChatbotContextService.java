package com.hotel_booking.service;

import com.hotel_booking.entity.enums.ChatbotIntent;

public interface ChatbotContextService {
    String getVerifiedContext(ChatbotIntent intent, String message);
}
