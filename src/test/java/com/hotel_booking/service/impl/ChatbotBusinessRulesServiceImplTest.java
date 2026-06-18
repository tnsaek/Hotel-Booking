package com.hotel_booking.service.impl;

import com.hotel_booking.entity.enums.ChatbotIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChatbotBusinessRulesServiceImplTest {

    private ChatbotBusinessRulesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatbotBusinessRulesServiceImpl();
    }

    @ParameterizedTest
    @CsvSource({
            "AVAILABILITY,Availability rules:,Room availability must come from verified backend room or external provider data.",
            "PRICING,Pricing rules:,Prices must come from verified backend pricing",
            "CANCELLATION_POLICY,Cancellation policy:,Refund eligibility depends on the booked room",
            "AMENITIES,Amenities information:,The chatbot may answer amenities questions only from verified hotel or room data.",
            "BOOKING_HELP,Booking help:,The chatbot can explain the booking steps but must not create",
            "ACCOUNT_HELP,Account-help rules:,The chatbot must not ask for or display passwords",
            "SUPPORT_HANDOFF,Support handoff:,Hand off to human support for payment disputes"
    })
    void getTrustedFactsReturnsIntentSpecificRules(
            ChatbotIntent intent,
            String expectedHeading,
            String expectedDetail
    ) {
        String trustedFacts = service.getTrustedFacts(intent);

        assertThat(trustedFacts)
                .contains(expectedHeading)
                .contains(expectedDetail);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "UNKNOWN")
    void getTrustedFactsReturnsDefaultRulesForNullOrUnmappedIntent(ChatbotIntent intent) {
        String trustedFacts = service.getTrustedFacts(intent);

        assertThat(trustedFacts)
                .contains("The chatbot can help with room availability")
                .contains("If verified hotel data is not available")
                .contains("The chatbot must not invent room availability");
    }
}
