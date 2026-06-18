package com.hotel_booking.service;

import com.hotel_booking.entity.enums.ChatbotIntent;

public interface ChatbotBusinessRulesService {
    String getTrustedFacts(ChatbotIntent intent);
}
