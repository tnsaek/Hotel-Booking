package com.hotel_booking.service.impl;

import com.hotel_booking.entity.enums.ChatbotIntent;
import com.hotel_booking.service.ChatbotBusinessRulesService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ChatbotBusinessRulesServiceImpl implements ChatbotBusinessRulesService {

    private static final String DEFAULT_FACTS = """
            The chatbot can help with room availability, booking guidance, cancellation policy, amenities, pricing questions, account help, and support handoff.
            If verified hotel data is not available, the chatbot must say that it does not have enough information.
            The chatbot must not invent room availability, prices, booking status, refund eligibility, or account-specific details.
            """;

    private static final Map<ChatbotIntent, String> TRUSTED_FACTS_BY_INTENT = Map.of(
            ChatbotIntent.CANCELLATION_POLICY, """
                    Cancellation policy:
                    - Guests can request cancellation help from the booking area of the application.
                    - Refund eligibility depends on the booked room, dates, payment status, and hotel policy stored in the booking system.
                    - The chatbot must not promise a refund unless refund eligibility is confirmed by backend booking data.
                    - If the user has a payment dispute, refund complaint, or urgent cancellation issue, hand off to human support.
                    """,
            ChatbotIntent.AMENITIES, """
                    Amenities information:
                    - The chatbot may answer amenities questions only from verified hotel or room data.
                    - Common amenities may include Wi-Fi, breakfast, parking, pool, gym, shuttle, spa, and accessibility options when listed by the hotel.
                    - The chatbot must not claim an amenity is available unless the application data confirms it.
                    - If amenity data is missing, ask the user to select a hotel or contact support.
                    """,
            ChatbotIntent.BOOKING_HELP, """
                    Booking help:
                    - Users should select destination or hotel, check-in date, check-out date, guest count, and room type before booking.
                    - Users should review room details, total price, cancellation terms, and payment requirements before confirming.
                    - The chatbot can explain the booking steps but must not create, modify, or cancel bookings by itself.
                    - For booking changes, payment issues, or failed confirmations, hand off to support or direct the user to the booking page.
                    """,
            ChatbotIntent.ACCOUNT_HELP, """
                    Account-help rules:
                    - The chatbot can give general guidance for login, registration, password reset, and profile access.
                    - The chatbot must not ask for or display passwords, payment card details, verification codes, or sensitive personal data.
                    - Account-specific booking details require the user to be authenticated.
                    - If the user cannot access the account or reports suspicious activity, hand off to support.
                    """,
            ChatbotIntent.SUPPORT_HANDOFF, """
                    Support handoff:
                    - Hand off to human support for payment disputes, legal issues, complaints, suspicious account activity, failed bookings, complex refunds, or repeated unclear requests.
                    - Ask the user to provide booking reference, account email, and a short issue description only through the official support flow.
                    - Do not collect passwords, card numbers, or one-time codes in chat.
                    - If no live-agent integration exists, direct the user to the application's support contact option.
                    """,
            ChatbotIntent.PRICING, """
                    Pricing rules:
                    - Prices must come from verified backend pricing, room, booking, or external provider data.
                    - The chatbot must not estimate, invent, or guarantee prices.
                    - Final price may depend on dates, room type, guest count, taxes, fees, availability, currency, and provider rules.
                    - If dates, room type, or guest count are missing, ask the user for those details.
                    """,
            ChatbotIntent.AVAILABILITY, """
                    Availability rules:
                    - Room availability must come from verified backend room or external provider data.
                    - The chatbot must not invent available rooms.
                    - Availability depends on destination or hotel, check-in date, check-out date, guest count, and room count.
                    - If required search details are missing, ask the user for the missing details.
                    """
    );

    @Override
    public String getTrustedFacts(ChatbotIntent intent) {
        if (intent == null) {
            return DEFAULT_FACTS;
        }
        return TRUSTED_FACTS_BY_INTENT.getOrDefault(intent, DEFAULT_FACTS);
    }
}
