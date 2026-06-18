package com.hotel_booking.service.impl;

import com.hotel_booking.entity.enums.ChatbotIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChatbotIntentServiceImplTest {

    private ChatbotIntentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatbotIntentServiceImpl();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void detectIntentReturnsUnknownForBlankMessages(String message) {
        assertThat(service.detectIntent(message)).isEqualTo(ChatbotIntent.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({
            "I need a human agent,SUPPORT_HANDOFF",
            "Please help with my login,ACCOUNT_HELP",
            "Can I cancel this booking?,CANCELLATION_POLICY",
            "Are any rooms available?,AVAILABILITY",
            "What is the lowest price?,PRICING",
            "Does the hotel have wi-fi?,AMENITIES",
            "I want to reserve a room,BOOKING_HELP"
    })
    void detectIntentReturnsExpectedIntentForKeywordGroups(String message, ChatbotIntent expectedIntent) {
        assertThat(service.detectIntent(message)).isEqualTo(expectedIntent);
    }

    @Test
    void detectIntentNormalizesCaseAndSurroundingWhitespace() {
        assertThat(service.detectIntent("   HOW MUCH does a suite COST?   "))
                .isEqualTo(ChatbotIntent.PRICING);
    }

    @Test
    void detectIntentReturnsUnknownWhenNoKeywordMatches() {
        assertThat(service.detectIntent("Tell me something interesting."))
                .isEqualTo(ChatbotIntent.UNKNOWN);
    }

    @Test
    void detectIntentUsesFirstMatchingIntentWhenMessageContainsMultipleIntentKeywords() {
        assertThat(service.detectIntent("I have a payment dispute and need a refund."))
                .isEqualTo(ChatbotIntent.SUPPORT_HANDOFF);
    }
}
