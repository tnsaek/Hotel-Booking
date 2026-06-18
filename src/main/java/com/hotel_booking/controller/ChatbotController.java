package com.hotel_booking.controller;

import com.hotel_booking.dto.request.ChatbotRequest;
import com.hotel_booking.dto.response.ChatbotResponse;
import com.hotel_booking.service.ChatbotOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ChatbotController {

    private final ChatbotOrchestratorService chatbotOrchestratorService;

    @PostMapping("/message")
    public ChatbotResponse message(@Valid @RequestBody ChatbotRequest request) {
        return ChatbotResponse.builder()
                .response(chatbotOrchestratorService.generateResponse(
                        request.getMessage(),
                        request.getConversationHistory()
                ))
                .build();
    }
}
