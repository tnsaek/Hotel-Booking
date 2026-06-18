package com.hotel_booking.controller;

import com.hotel_booking.dto.request.ChatbotRequest;
import com.hotel_booking.dto.request.ChatbotConversationMessage;
import com.hotel_booking.dto.response.ChatbotResponse;
import com.hotel_booking.service.ChatbotOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotControllerTest {

    @Mock
    private ChatbotOrchestratorService chatbotOrchestratorService;

    private ChatbotController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatbotController(chatbotOrchestratorService);
    }

    @Test
    void messageReturnsGeneratedChatbotResponse() {
        ChatbotRequest request = ChatbotRequest.builder()
                .message("How do I cancel my booking?")
                .conversationHistory(List.of(
                        ChatbotConversationMessage.builder()
                                .role("user")
                                .content("I booked a suite yesterday.")
                                .build()
                ))
                .build();
        when(chatbotOrchestratorService.generateResponse("How do I cancel my booking?", request.getConversationHistory()))
                .thenReturn("Open your booking details and request cancellation there.");

        ChatbotResponse response = controller.message(request);

        assertThat(response.getResponse()).isEqualTo("Open your booking details and request cancellation there.");
        verify(chatbotOrchestratorService).generateResponse("How do I cancel my booking?", request.getConversationHistory());
    }
}
