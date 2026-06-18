package com.hotel_booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequest {
    @NotBlank(message = "Message is required")
    private String message;
    private List<ChatbotConversationMessage> conversationHistory;
}
