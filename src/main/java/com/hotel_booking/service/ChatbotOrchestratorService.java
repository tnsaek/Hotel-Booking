package com.hotel_booking.service;

import com.hotel_booking.dto.request.ChatbotConversationMessage;

import java.util.List;

public interface ChatbotOrchestratorService {
    String generateResponse(String message);
    String generateResponse(String message, List<ChatbotConversationMessage> conversationHistory);
}
