package com.hotel_booking.service.impl;

import com.hotel_booking.dto.ExternalHotelOfferDto;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.enums.ChatbotIntent;
import com.hotel_booking.repository.HotelRepository;
import com.hotel_booking.repository.RoomRepository;
import com.hotel_booking.service.ChatbotContextService;
import com.hotel_booking.service.LiteApiHotelSearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatbotContextServiceImpl implements ChatbotContextService {

    private static final int CONTEXT_LIMIT = 5;
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern GUEST_PATTERN = Pattern.compile("\\b(\\d+|one|two|three|four|five|six|single)\\s+(guest|guests|adult|adults|person|people)\\b");
    private static final Pattern CITY_STATE_PATTERN = Pattern.compile("\\b(?:in|near|around|at)\\s+([a-z][a-z .'-]+?),\\s*([a-z][a-z .'-]+)\\b");
    private static final Pattern CITY_STATE_SHORT_PATTERN = Pattern.compile("\\b(charlotte)\\s*,\\s*(north carolina|nc)\\b");
    private static final Pattern CITY_STATE_WITHOUT_COMMA_PATTERN = Pattern.compile("\\b(?:in|near|around|at)?\\s*(charlotte)\\s+(north carolina|nc)\\b");
    private static final String NO_VERIFIED_CONTEXT = """
            No verified live hotel, room, pricing, booking, payment, or user-specific data was available for this message.
            Ask the user for missing search details or direct them to the relevant application flow.
            """;

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final LiteApiHotelSearchService liteApiHotelSearchService;

    public ChatbotContextServiceImpl(
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            LiteApiHotelSearchService liteApiHotelSearchService
    ) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.liteApiHotelSearchService = liteApiHotelSearchService;
    }

    @Override
    public String getVerifiedContext(ChatbotIntent intent, String message) {
        if (intent == null) {
            return NO_VERIFIED_CONTEXT;
        }

        return switch (intent) {
            case AVAILABILITY -> availableRoomContext(message);
            case PRICING -> pricingContext(message);
            case AMENITIES -> amenitiesContext(message);
            default -> NO_VERIFIED_CONTEXT;
        };
    }

    private String availableRoomContext(String message) {
        Optional<SearchCriteria> searchCriteria = extractSearchCriteria(message);
        if (searchCriteria.isPresent()) {
            return liteApiOfferContext(searchCriteria.get());
        }
        Optional<String> missingCriteriaContext = missingSearchCriteriaContext(message);
        if (missingCriteriaContext.isPresent()) {
            return missingCriteriaContext.get();
        }

        List<Room> rooms = filterRoomsForMessage(roomRepository.findByAvailableTrue(), message);
        if (rooms.isEmpty()) {
            return """
                    No verified available rooms matched the current message.
                    Ask for destination or hotel, check-in date, check-out date, guest count, and room count.
                    """;
        }

        StringBuilder context = new StringBuilder("Verified currently available rooms:\n");
        rooms.stream()
                .limit(CONTEXT_LIMIT)
                .map(this::roomSummary)
                .forEach(summary -> context.append("- ").append(summary).append('\n'));
        context.append("Availability is based only on the room available flag and does not confirm date-specific availability.");
        return context.toString();
    }

    private String pricingContext(String message) {
        Optional<SearchCriteria> searchCriteria = extractSearchCriteria(message);
        if (searchCriteria.isPresent()) {
            return liteApiOfferContext(searchCriteria.get());
        }
        Optional<String> missingCriteriaContext = missingSearchCriteriaContext(message);
        if (missingCriteriaContext.isPresent()) {
            return missingCriteriaContext.get();
        }

        List<Room> rooms = filterRoomsForMessage(roomRepository.findAll(), message).stream()
                .filter(room -> room.getPricePerNight() != null)
                .toList();
        if (rooms.isEmpty()) {
            return """
                    No verified room prices matched the current message.
                    Ask for destination or hotel, dates, guest count, and room type before discussing exact pricing.
                    """;
        }

        StringBuilder context = new StringBuilder("Verified room prices from the application database:\n");
        rooms.stream()
                .limit(CONTEXT_LIMIT)
                .map(this::roomSummary)
                .forEach(summary -> context.append("- ").append(summary).append('\n'));
        context.append("Prices may change with dates, taxes, fees, availability, and provider rules.");
        return context.toString();
    }

    private String liteApiOfferContext(SearchCriteria criteria) {
        try {
            List<ExternalHotelOfferDto> offers = liteApiHotelSearchService.search(
                    criteria.cityName(),
                    criteria.countryCode(),
                    criteria.checkInDate(),
                    criteria.checkOutDate(),
                    criteria.adults(),
                    1,
                    "USD",
                    "US"
            );

            if (offers.isEmpty()) {
                return "LiteAPI returned no verified hotel offers for %s, %s from %s to %s for %d guest(s)."
                        .formatted(
                                criteria.cityName(),
                                criteria.countryCode(),
                                criteria.checkInDate(),
                                criteria.checkOutDate(),
                                criteria.adults()
                        );
            }

            StringBuilder context = new StringBuilder(
                    "Verified LiteAPI hotel offers for %s, %s from %s to %s for %d guest(s):\n"
                            .formatted(
                                    criteria.cityName(),
                                    criteria.countryCode(),
                                    criteria.checkInDate(),
                                    criteria.checkOutDate(),
                                    criteria.adults()
                            )
            );

            offers.stream()
                    .sorted(Comparator.comparing(this::priceValue, Comparator.nullsLast(Double::compareTo)))
                    .limit(CONTEXT_LIMIT)
                    .map(this::offerSummary)
                    .forEach(summary -> context.append("- ").append(summary).append('\n'));
            context.append("These offers came from LiteAPI. Present them as the available verified options and mention that final price can change before checkout.");
            return context.toString();
        } catch (RuntimeException exception) {
            return "LiteAPI hotel search could not be completed for %s, %s from %s to %s for %d guest(s): %s"
                    .formatted(
                            criteria.cityName(),
                            criteria.countryCode(),
                            criteria.checkInDate(),
                            criteria.checkOutDate(),
                            criteria.adults(),
                            exception.getMessage()
                    );
        }
    }

    private String amenitiesContext(String message) {
        List<Hotel> hotels = filterHotelsForMessage(hotelRepository.findAll(PageRequest.of(0, CONTEXT_LIMIT)).getContent(), message);
        if (hotels.isEmpty()) {
            return """
                    No verified hotel amenity details matched the current message.
                    Ask the user to choose a hotel or view the hotel details page.
                    """;
        }

        StringBuilder context = new StringBuilder("Verified hotel details that may include amenities:\n");
        hotels.stream()
                .limit(CONTEXT_LIMIT)
                .map(this::hotelSummary)
                .forEach(summary -> context.append("- ").append(summary).append('\n'));
        context.append("Only mention amenities that are explicitly present in these verified hotel details.");
        return context.toString();
    }

    private List<Room> filterRoomsForMessage(List<Room> rooms, String message) {
        if (!StringUtils.hasText(message)) {
            return rooms.stream().limit(CONTEXT_LIMIT).toList();
        }

        String normalizedMessage = normalize(message);
        List<Room> matchedRooms = rooms.stream()
                .filter(room -> containsRoomMatch(room, normalizedMessage))
                .limit(CONTEXT_LIMIT)
                .toList();

        if (!matchedRooms.isEmpty()) {
            return matchedRooms;
        }

        return rooms.stream().limit(CONTEXT_LIMIT).toList();
    }

    private List<Hotel> filterHotelsForMessage(List<Hotel> hotels, String message) {
        if (!StringUtils.hasText(message)) {
            return hotels;
        }

        String normalizedMessage = normalize(message);
        List<Hotel> matchedHotels = hotels.stream()
                .filter(hotel -> contains(normalizedMessage, hotel.getName())
                        || contains(normalizedMessage, hotel.getLocation())
                        || contains(normalizedMessage, hotel.getDescription()))
                .limit(CONTEXT_LIMIT)
                .toList();

        if (!matchedHotels.isEmpty()) {
            return matchedHotels;
        }

        return hotels;
    }

    private boolean containsRoomMatch(Room room, String normalizedMessage) {
        Hotel hotel = room.getHotel();
        return contains(normalizedMessage, room.getType() == null ? null : room.getType().name())
                || contains(normalizedMessage, room.getDescription())
                || contains(normalizedMessage, hotel == null ? null : hotel.getName())
                || contains(normalizedMessage, hotel == null ? null : hotel.getLocation())
                || contains(normalizedMessage, room.getRoomNumber() == null ? null : String.valueOf(room.getRoomNumber()));
    }

    private boolean contains(String normalizedMessage, String value) {
        return StringUtils.hasText(value) && normalizedMessage.contains(normalize(value));
    }

    private String roomSummary(Room room) {
        Hotel hotel = room.getHotel();
        return "roomNumber=%s, type=%s, available=%s, pricePerNight=%s, hotel=%s, location=%s, description=%s"
                .formatted(
                        text(room.getRoomNumber()),
                        room.getType() == null ? "unknown" : room.getType().name(),
                        room.isAvailable(),
                        text(room.getPricePerNight()),
                        hotel == null ? "unknown" : text(hotel.getName()),
                        hotel == null ? "unknown" : text(hotel.getLocation()),
                        text(room.getDescription())
                );
    }

    private String hotelSummary(Hotel hotel) {
        return "hotel=%s, location=%s, description=%s"
                .formatted(text(hotel.getName()), text(hotel.getLocation()), text(hotel.getDescription()));
    }

    private String offerSummary(ExternalHotelOfferDto offer) {
        return "hotel=%s, address=%s, roomType=%s, priceTotal=%s, currency=%s, checkIn=%s, checkOut=%s, adults=%s, description=%s"
                .formatted(
                        text(offer.getName()),
                        text(offer.getAddress()),
                        text(offer.getRoomType()),
                        text(offer.getPriceTotal()),
                        text(offer.getCurrency()),
                        text(offer.getCheckInDate()),
                        text(offer.getCheckOutDate()),
                        text(offer.getAdults()),
                        text(offer.getDescription())
                );
    }

    private Optional<SearchCriteria> extractSearchCriteria(String message) {
        if (!StringUtils.hasText(message)) {
            return Optional.empty();
        }

        String normalizedMessage = normalize(message);
        Optional<CityCountry> cityCountry = extractCityCountry(normalizedMessage);
        Optional<LocalDate> checkInDate = extractCheckInDate(normalizedMessage);
        int adults = extractAdults(normalizedMessage).orElse(1);

        if (cityCountry.isEmpty() || checkInDate.isEmpty()) {
            return Optional.empty();
        }

        LocalDate checkOutDate = extractCheckOutDate(normalizedMessage, checkInDate.get());
        return Optional.of(new SearchCriteria(
                cityCountry.get().cityName(),
                cityCountry.get().countryCode(),
                checkInDate.get(),
                checkOutDate,
                adults
        ));
    }

    private Optional<String> missingSearchCriteriaContext(String message) {
        if (!StringUtils.hasText(message)) {
            return Optional.empty();
        }

        String normalizedMessage = normalize(message);
        Optional<CityCountry> cityCountry = extractCityCountry(normalizedMessage);
        Optional<LocalDate> checkInDate = extractCheckInDate(normalizedMessage);

        if (cityCountry.isEmpty() && checkInDate.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder context = new StringBuilder("The user appears to be searching for hotel availability or pricing");
        cityCountry.ifPresent(country -> context.append(" in ")
                .append(country.cityName())
                .append(", ")
                .append(country.countryCode()));
        checkInDate.ifPresent(date -> context.append(" with check-in date ").append(date));
        context.append(". ");

        if (cityCountry.isEmpty()) {
            context.append("Missing destination or hotel. ");
        }
        if (checkInDate.isEmpty()) {
            context.append("Missing check-in date. ");
        }
        if (extractAdults(normalizedMessage).isEmpty()) {
            context.append("Missing guest count. ");
        }

        context.append("Do not say live offers were checked yet. Ask only for the missing details needed to run a LiteAPI search.");
        return Optional.of(context.toString());
    }

    private Optional<CityCountry> extractCityCountry(String normalizedMessage) {
        Matcher matcher = CITY_STATE_PATTERN.matcher(normalizedMessage);
        CityCountry lastMatch = null;
        while (matcher.find()) {
            String city = toTitleCase(cleanLocation(matcher.group(1)));
            String region = cleanLocation(matcher.group(2));
            String countryCode = countryCodeForRegion(region);
            if (StringUtils.hasText(countryCode)) {
                lastMatch = new CityCountry(city, countryCode);
            }
        }
        Matcher shortMatcher = CITY_STATE_SHORT_PATTERN.matcher(normalizedMessage);
        while (shortMatcher.find()) {
            String city = toTitleCase(cleanLocation(shortMatcher.group(1)));
            String countryCode = countryCodeForRegion(cleanLocation(shortMatcher.group(2)));
            lastMatch = new CityCountry(city, countryCode);
        }
        Matcher noCommaMatcher = CITY_STATE_WITHOUT_COMMA_PATTERN.matcher(normalizedMessage);
        while (noCommaMatcher.find()) {
            String city = toTitleCase(cleanLocation(noCommaMatcher.group(1)));
            String countryCode = countryCodeForRegion(cleanLocation(noCommaMatcher.group(2)));
            lastMatch = new CityCountry(city, countryCode);
        }
        return Optional.ofNullable(lastMatch);
    }

    private Optional<LocalDate> extractCheckInDate(String normalizedMessage) {
        Matcher matcher = ISO_DATE_PATTERN.matcher(normalizedMessage);
        if (matcher.find()) {
            return Optional.of(LocalDate.parse(matcher.group(1)));
        }

        LocalDate today = LocalDate.now();
        if (normalizedMessage.contains("sunday")) {
            return Optional.of(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
        }
        if (normalizedMessage.contains("monday")) {
            return Optional.of(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)));
        }
        if (normalizedMessage.contains("tuesday")) {
            return Optional.of(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY)));
        }
        if (normalizedMessage.contains("wednesday")) {
            return Optional.of(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY)));
        }
        if (normalizedMessage.contains("thursday")) {
            return Optional.of(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY)));
        }
        if (normalizedMessage.contains("friday")) {
            return Optional.of(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)));
        }
        if (normalizedMessage.contains("saturday")) {
            return Optional.of(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)));
        }
        if (normalizedMessage.contains("tomorrow")) {
            return Optional.of(today.plusDays(1));
        }

        return Optional.empty();
    }

    private LocalDate extractCheckOutDate(String normalizedMessage, LocalDate checkInDate) {
        Matcher matcher = ISO_DATE_PATTERN.matcher(normalizedMessage);
        LocalDate lastDate = null;
        while (matcher.find()) {
            lastDate = LocalDate.parse(matcher.group(1));
        }
        if (lastDate != null && lastDate.isAfter(checkInDate)) {
            return lastDate;
        }

        return checkInDate.plusDays(1);
    }

    private Optional<Integer> extractAdults(String normalizedMessage) {
        Matcher matcher = GUEST_PATTERN.matcher(normalizedMessage);
        Integer adults = null;
        while (matcher.find()) {
            adults = numberValue(matcher.group(1));
        }
        return Optional.ofNullable(adults);
    }

    private Integer numberValue(String value) {
        return switch (value) {
            case "single" -> 1;
            case "one" -> 1;
            case "two" -> 2;
            case "three" -> 3;
            case "four" -> 4;
            case "five" -> 5;
            case "six" -> 6;
            default -> Integer.parseInt(value);
        };
    }

    private String countryCodeForRegion(String region) {
        String normalizedRegion = normalize(region);
        if (normalizedRegion.equals("north carolina")
                || normalizedRegion.equals("nc")
                || normalizedRegion.equals("united states")
                || normalizedRegion.equals("usa")
                || normalizedRegion.equals("us")) {
            return "US";
        }
        if (normalizedRegion.length() == 2) {
            return normalizedRegion.toUpperCase(Locale.ROOT);
        }
        return "";
    }

    private String cleanLocation(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^a-zA-Z .'-]", " ").replaceAll("\\s+", " ").trim();
    }

    private String toTitleCase(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String[] words = value.trim().split("\\s+");
        for (int index = 0; index < words.length; index++) {
            words[index] = words[index].substring(0, 1).toUpperCase(Locale.ROOT)
                    + words[index].substring(1).toLowerCase(Locale.ROOT);
        }
        return String.join(" ", words);
    }

    private Double priceValue(ExternalHotelOfferDto offer) {
        if (!StringUtils.hasText(offer.getPriceTotal())) {
            return null;
        }
        try {
            return Double.parseDouble(offer.getPriceTotal());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? "unknown" : String.valueOf(value).trim();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).trim();
    }

    private record SearchCriteria(
            String cityName,
            String countryCode,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Integer adults
    ) {
    }

    private record CityCountry(String cityName, String countryCode) {
    }
}
