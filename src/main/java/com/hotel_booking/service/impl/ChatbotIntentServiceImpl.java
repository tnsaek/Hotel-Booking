package com.hotel_booking.service.impl;

import com.hotel_booking.entity.enums.ChatbotIntent;
import com.hotel_booking.service.ChatbotIntentService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ChatbotIntentServiceImpl implements ChatbotIntentService {

    private static final Map<ChatbotIntent, Set<String>> INTENT_KEYWORDS = orderedIntentKeywords();

    @Override
    public ChatbotIntent detectIntent(String message) {
        if (!StringUtils.hasText(message)) {
            return ChatbotIntent.UNKNOWN;
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT).trim();
        return INTENT_KEYWORDS.entrySet()
                .stream()
                .filter(entry -> containsAny(normalizedMessage, entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(ChatbotIntent.UNKNOWN);
    }

    private boolean containsAny(String message, Set<String> keywords) {
        return keywords.stream().anyMatch(message::contains);
    }

    private static Map<ChatbotIntent, Set<String>> orderedIntentKeywords() {
        Map<ChatbotIntent, Set<String>> keywords = new LinkedHashMap<>();
        keywords.put(ChatbotIntent.SUPPORT_HANDOFF, Set.of(
                "agent", "human", "representative", "support", "complaint", "dispute", "legal", "manager"
        ));
        keywords.put(ChatbotIntent.ACCOUNT_HELP, Set.of(
                "account", "login", "log in", "sign in", "password", "profile", "email", "username"
        ));
        keywords.put(ChatbotIntent.CANCELLATION_POLICY, Set.of(
                "cancel", "cancellation", "refund", "refundable", "reschedule", "change booking"
        ));
        keywords.put(ChatbotIntent.AVAILABILITY, Set.of(
                "available", "availability", "vacancy", "vacancies", "room available", "rooms available"
        ));
        keywords.put(ChatbotIntent.PRICING, Set.of(
                "price", "pricing", "cost", "rate", "rates", "fee", "fees", "charge", "charges", "how much",
                "cheap", "budget", "affordable", "low price", "lowest price"
        ));
        keywords.put(ChatbotIntent.AMENITIES, Set.of(
                "amenity", "amenities", "wifi", "wi-fi", "breakfast", "pool", "gym", "parking", "shuttle", "spa"
        ));
        keywords.put(ChatbotIntent.BOOKING_HELP, Set.of(
                "book", "booking", "reservation", "reserve", "checkout", "check out", "check-in", "check in"
        ));
        return Collections.unmodifiableMap(keywords);
    }
}
