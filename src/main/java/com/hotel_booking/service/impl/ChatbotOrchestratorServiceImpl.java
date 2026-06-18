package com.hotel_booking.service.impl;

import com.hotel_booking.dto.request.ChatbotConversationMessage;
import com.hotel_booking.entity.enums.ChatbotIntent;
import com.hotel_booking.service.ChatbotBusinessRulesService;
import com.hotel_booking.service.ChatbotContextService;
import com.hotel_booking.service.ChatbotIntentService;
import com.hotel_booking.service.ChatbotOrchestratorService;
import com.hotel_booking.service.GeminiService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class ChatbotOrchestratorServiceImpl implements ChatbotOrchestratorService {

    private static final int HISTORY_LIMIT = 10;
    private static final String RATE_LIMIT_MESSAGE =
            "The hotel assistant is temporarily busy. Please wait a moment and try again.";

    private final ChatbotIntentService chatbotIntentService;
    private final ChatbotBusinessRulesService chatbotBusinessRulesService;
    private final ChatbotContextService chatbotContextService;
    private final GeminiService geminiService;

    public ChatbotOrchestratorServiceImpl(
            ChatbotIntentService chatbotIntentService,
            ChatbotBusinessRulesService chatbotBusinessRulesService,
            ChatbotContextService chatbotContextService,
            GeminiService geminiService
    ) {
        this.chatbotIntentService = chatbotIntentService;
        this.chatbotBusinessRulesService = chatbotBusinessRulesService;
        this.chatbotContextService = chatbotContextService;
        this.geminiService = geminiService;
    }

    @Override
    public String generateResponse(String message) {
        return generateResponse(message, List.of());
    }

    @Override
    public String generateResponse(String message, List<ChatbotConversationMessage> conversationHistory) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("Message is required");
        }

        String trimmedMessage = message.trim();
        String formattedHistory = formatConversationHistory(conversationHistory);
        String userHistory = formatUserConversationHistory(conversationHistory);
        String userConversationAwareMessage = userHistory.isBlank()
                ? trimmedMessage
                : userHistory + "\nCurrent user message: " + trimmedMessage;
        ChatbotIntent intent = detectIntent(trimmedMessage, userConversationAwareMessage);
        String trustedFacts = chatbotBusinessRulesService.getTrustedFacts(intent);
        String verifiedContext = chatbotContextService.getVerifiedContext(intent, userConversationAwareMessage);

        try {
            return geminiService.generateText(buildPrompt(trimmedMessage, formattedHistory, intent, trustedFacts, verifiedContext));
        } catch (IllegalStateException exception) {
            if (isRateLimitError(exception)) {
                return RATE_LIMIT_MESSAGE;
            }
            throw exception;
        }
    }

    private boolean isRateLimitError(IllegalStateException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("429") || message.contains("TOO_MANY_REQUESTS"));
    }

    private String buildPrompt(
            String message,
            String conversationHistory,
            ChatbotIntent intent,
            String trustedFacts,
            String verifiedContext
    ) {
        return """
                You are the hotel booking assistant for this application.
                Answer using only the trusted business rules and facts below.
                If the trusted facts do not contain enough information, say that you do not have enough verified information and ask for the missing details.
                Do not invent room availability, prices, booking status, refund eligibility, policies, amenities, or account-specific details.
                Do not ask for passwords, payment card numbers, one-time codes, or other sensitive data.
                Use the conversation history to understand follow-up messages and keep previously provided details, such as destination, dates, guest count, budget, and room preferences.
                If a user gives a relative date like "Sunday", resolve it from the current date when possible and state the resolved date.
                If the verified application context includes LiteAPI hotel offers, answer with those concrete options instead of saying you are checking or waiting.
                If the verified application context says details are missing, ask only for those missing details and do not imply that offers have already been found.
                Keep the response concise, helpful, and action-oriented.

                Current date: %s
                Detected intent: %s

                Conversation history:
                %s

                Trusted business rules and facts:
                %s

                Verified application context:
                %s

                User message:
                %s
                """.formatted(
                LocalDate.now(),
                intent == null ? ChatbotIntent.UNKNOWN : intent,
                StringUtils.hasText(conversationHistory) ? conversationHistory : "No prior conversation history was provided.",
                trustedFacts,
                verifiedContext,
                message
        );
    }

    private String formatConversationHistory(List<ChatbotConversationMessage> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return "";
        }

        return conversationHistory.stream()
                .filter(message -> message != null && StringUtils.hasText(message.getContent()))
                .skip(Math.max(0, conversationHistory.size() - HISTORY_LIMIT))
                .map(message -> normalizedRole(message.getRole()) + ": " + message.getContent().trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatUserConversationHistory(List<ChatbotConversationMessage> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return "";
        }

        return conversationHistory.stream()
                .filter(message -> message != null
                        && StringUtils.hasText(message.getContent())
                        && !"assistant".equals(normalizedRole(message.getRole())))
                .skip(Math.max(0, conversationHistory.size() - HISTORY_LIMIT))
                .map(message -> "user: " + message.getContent().trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private ChatbotIntent detectIntent(String currentMessage, String userConversationAwareMessage) {
        ChatbotIntent currentIntent = chatbotIntentService.detectIntent(currentMessage);
        if (currentIntent != ChatbotIntent.UNKNOWN) {
            return currentIntent;
        }
        return chatbotIntentService.detectIntent(userConversationAwareMessage);
    }

    private String normalizedRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }

        String normalizedRole = role.toLowerCase(Locale.ROOT).trim();
        if ("assistant".equals(normalizedRole)) {
            return "assistant";
        }
        return "user";
    }
}
