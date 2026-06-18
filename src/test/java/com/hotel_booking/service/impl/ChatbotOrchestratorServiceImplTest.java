package com.hotel_booking.service.impl;

import com.hotel_booking.dto.request.ChatbotConversationMessage;
import com.hotel_booking.entity.enums.ChatbotIntent;
import com.hotel_booking.service.ChatbotBusinessRulesService;
import com.hotel_booking.service.ChatbotContextService;
import com.hotel_booking.service.ChatbotIntentService;
import com.hotel_booking.service.GeminiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotOrchestratorServiceImplTest {

    @Mock
    private ChatbotIntentService chatbotIntentService;

    @Mock
    private ChatbotBusinessRulesService chatbotBusinessRulesService;

    @Mock
    private ChatbotContextService chatbotContextService;

    @Mock
    private GeminiService geminiService;

    private ChatbotOrchestratorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatbotOrchestratorServiceImpl(
                chatbotIntentService,
                chatbotBusinessRulesService,
                chatbotContextService,
                geminiService
        );
    }

    @Test
    void generateResponseIncludesConversationHistoryForFollowUpMessages() {
        List<ChatbotConversationMessage> history = List.of(
                ChatbotConversationMessage.builder()
                        .role("user")
                        .content("I need cheap hotels in Charlotte, North Carolina")
                        .build(),
                ChatbotConversationMessage.builder()
                        .role("assistant")
                        .content("Please provide check-in date, check-out date, and guests.")
                        .build()
        );
        String userConversationAwareMessage = """
                user: I need cheap hotels in Charlotte, North Carolina
                Current user message: I need a room for Sunday for one guest""";

        when(chatbotIntentService.detectIntent("I need a room for Sunday for one guest"))
                .thenReturn(ChatbotIntent.UNKNOWN);
        when(chatbotIntentService.detectIntent(userConversationAwareMessage))
                .thenReturn(ChatbotIntent.PRICING);
        when(chatbotBusinessRulesService.getTrustedFacts(ChatbotIntent.PRICING))
                .thenReturn("Prices must come from verified backend pricing.");
        when(chatbotContextService.getVerifiedContext(ChatbotIntent.PRICING, userConversationAwareMessage))
                .thenReturn("No verified room prices matched the current message.");
        when(geminiService.generateText(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("For Charlotte on Sunday for one guest, I need the exact Sunday date.");

        String response = service.generateResponse("I need a room for Sunday for one guest", history);

        assertThat(response).isEqualTo("For Charlotte on Sunday for one guest, I need the exact Sunday date.");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiService).generateText(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("Conversation history:")
                .contains("user: I need cheap hotels in Charlotte, North Carolina")
                .contains("assistant: Please provide check-in date, check-out date, and guests.")
                .contains("Use the conversation history to understand follow-up messages")
                .contains("User message:")
                .contains("I need a room for Sunday for one guest");
    }

    @Test
    void generateResponseCombinesIntentBusinessRulesAndGeminiGeneration() {
        when(chatbotIntentService.detectIntent("Can I cancel my booking?"))
                .thenReturn(ChatbotIntent.CANCELLATION_POLICY);
        when(chatbotBusinessRulesService.getTrustedFacts(ChatbotIntent.CANCELLATION_POLICY))
                .thenReturn("Refunds require verified booking and payment data.");
        when(chatbotContextService.getVerifiedContext(ChatbotIntent.CANCELLATION_POLICY, "Can I cancel my booking?"))
                .thenReturn("No booking-specific context is available from this public chatbot endpoint.");
        when(geminiService.generateText(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("You can request cancellation from the booking area.");

        String response = service.generateResponse("  Can I cancel my booking?  ");

        assertThat(response).isEqualTo("You can request cancellation from the booking area.");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiService).generateText(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("Detected intent: CANCELLATION_POLICY")
                .contains("Refunds require verified booking and payment data.")
                .contains("Verified application context:")
                .contains("No booking-specific context is available from this public chatbot endpoint.")
                .contains("User message:")
                .contains("Can I cancel my booking?")
                .contains("Do not invent room availability, prices, booking status, refund eligibility, policies, amenities, or account-specific details.");
    }

    @Test
    void generateResponseReturnsFriendlyMessageWhenGeminiIsRateLimited() {
        when(chatbotIntentService.detectIntent("Find cheap hotels in Charlotte"))
                .thenReturn(ChatbotIntent.PRICING);
        when(chatbotBusinessRulesService.getTrustedFacts(ChatbotIntent.PRICING))
                .thenReturn("Prices must come from verified backend pricing.");
        when(chatbotContextService.getVerifiedContext(ChatbotIntent.PRICING, "Find cheap hotels in Charlotte"))
                .thenReturn("Missing check-in date and guest count.");
        when(geminiService.generateText(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("Gemini API request failed with status 429 TOO_MANY_REQUESTS"));

        String response = service.generateResponse("Find cheap hotels in Charlotte");

        assertThat(response).isEqualTo("The hotel assistant is temporarily busy. Please wait a moment and try again.");
    }

    @Test
    void generateResponseRejectsBlankMessage() {
        assertThatThrownBy(() -> service.generateResponse(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Message is required");

        verify(chatbotIntentService, never()).detectIntent(org.mockito.ArgumentMatchers.anyString());
        verify(chatbotBusinessRulesService, never()).getTrustedFacts(org.mockito.ArgumentMatchers.any());
        verify(chatbotContextService, never()).getVerifiedContext(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(geminiService, never()).generateText(org.mockito.ArgumentMatchers.anyString());
    }
}
