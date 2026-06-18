package com.hotel_booking.service.impl;

import com.hotel_booking.dto.ExternalHotelOfferDto;
import com.hotel_booking.entity.Hotel;
import com.hotel_booking.entity.Room;
import com.hotel_booking.entity.enums.ChatbotIntent;
import com.hotel_booking.entity.enums.RoomType;
import com.hotel_booking.repository.HotelRepository;
import com.hotel_booking.repository.RoomRepository;
import com.hotel_booking.service.LiteApiHotelSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotContextServiceImplTest {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private LiteApiHotelSearchService liteApiHotelSearchService;

    private ChatbotContextServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatbotContextServiceImpl(hotelRepository, roomRepository, liteApiHotelSearchService);
    }

    @Test
    void availabilityContextIncludesVerifiedAvailableRoomData() {
        Hotel hotel = hotel("Grand Hotel", "Addis Ababa", "City center with parking");
        when(roomRepository.findByAvailableTrue()).thenReturn(List.of(
                room(101, RoomType.SUITE, 180.0, true, "Balcony and Wi-Fi", hotel)
        ));

        String context = service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "Is Grand Hotel available?");

        assertThat(context)
                .contains("Verified currently available rooms")
                .contains("roomNumber=101")
                .contains("type=SUITE")
                .contains("hotel=Grand Hotel")
                .contains("Availability is based only on the room available flag");
    }

    @Test
    void availabilityContextSearchesLiteApiWhenSearchCriteriaAreComplete() {
        LocalDate checkIn = LocalDate.of(2026, 7, 10);
        LocalDate checkOut = LocalDate.of(2026, 7, 11);
        when(liteApiHotelSearchService.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(ExternalHotelOfferDto.builder()
                        .name("Austin Stay")
                        .priceTotal("140.00")
                        .currency("USD")
                        .build()));

        String context = service.getVerifiedContext(
                ChatbotIntent.AVAILABILITY,
                "Available hotels in Austin, TX: 2026-07-10 for two guests"
        );

        assertThat(context)
                .contains("Verified LiteAPI hotel offers for Austin, TX")
                .contains("Austin Stay");
        verify(liteApiHotelSearchService).search(
                "Austin",
                "TX",
                checkIn,
                checkOut,
                2,
                1,
                "USD",
                "US"
        );
        verifyNoInteractions(roomRepository);
    }

    @Test
    void availabilityContextAsksForMissingSearchDetailsBeforeRepositoryFallback() {
        String context = service.getVerifiedContext(
                ChatbotIntent.AVAILABILITY,
                "Available hotels in Charlotte, NC"
        );

        assertThat(context)
                .contains("searching for hotel availability or pricing in Charlotte, US")
                .contains("Missing check-in date")
                .contains("Missing guest count");
        verifyNoInteractions(roomRepository, liteApiHotelSearchService);
    }

    @Test
    void pricingContextIncludesVerifiedRoomPrices() {
        Hotel hotel = hotel("City Hotel", "New York", "Near transit");
        when(roomRepository.findAll()).thenReturn(List.of(
                room(202, RoomType.DOUBLE, 120.0, true, "Queen bed", hotel)
        ));

        String context = service.getVerifiedContext(ChatbotIntent.PRICING, "How much is City Hotel?");

        assertThat(context)
                .contains("Verified room prices")
                .contains("pricePerNight=120.0")
                .contains("hotel=City Hotel")
                .contains("Prices may change");
    }

    @Test
    void pricingContextSearchesLiteApiWhenConversationHasEnoughSearchCriteria() {
        LocalDate nextSunday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate checkOut = nextSunday.plusDays(1);
        when(liteApiHotelSearchService.search(
                any(String.class),
                any(String.class),
                any(LocalDate.class),
                any(LocalDate.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(String.class)
        )).thenReturn(List.of(ExternalHotelOfferDto.builder()
                .provider("LiteAPI")
                .hotelId("hotel-1")
                .name("Charlotte Budget Inn")
                .address("100 Tryon Street")
                .roomType("Standard Queen")
                .priceTotal("89.00")
                .currency("USD")
                .checkInDate(nextSunday.toString())
                .checkOutDate(checkOut.toString())
                .adults(1)
                .description("Central Charlotte hotel")
                .build()));

        String context = service.getVerifiedContext(ChatbotIntent.PRICING, """
                user: i need a cheap hotel in Charlotte, North Carolina
                assistant: Please provide dates and guests.
                user: for sunday after tomorrow
                assistant: Please provide checkout date and guests.
                user: just one day and it is for one guest
                Current user message: I am waiting
                """);

        assertThat(context)
                .contains("Verified LiteAPI hotel offers for Charlotte, US")
                .contains("Charlotte Budget Inn")
                .contains("priceTotal=89.00")
                .contains("checkIn=" + nextSunday)
                .contains("checkOut=" + checkOut);
        verify(liteApiHotelSearchService).search(
                "Charlotte",
                "US",
                nextSunday,
                checkOut,
                1,
                1,
                "USD",
                "US"
        );
        verifyNoInteractions(roomRepository);
    }

    @Test
    void pricingContextAsksForMissingDetailsWhenDestinationHasNoCommaButDatesAreMissing() {
        String context = service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "need a cheap hotel in charlotte north carolina"
        );

        assertThat(context)
                .contains("searching for hotel availability or pricing in Charlotte, US")
                .contains("Missing check-in date")
                .contains("Missing guest count")
                .contains("Do not say live offers were checked yet");
        verifyNoInteractions(roomRepository, liteApiHotelSearchService);
    }

    @Test
    void pricingContextSearchesLiteApiForCharlotteWithoutCommaWhenDatesAndGuestsExist() {
        LocalDate nextSunday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate checkOut = nextSunday.plusDays(1);
        when(liteApiHotelSearchService.search(
                any(String.class),
                any(String.class),
                any(LocalDate.class),
                any(LocalDate.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(String.class)
        )).thenReturn(List.of(ExternalHotelOfferDto.builder()
                .name("Charlotte Budget Inn")
                .priceTotal("89.00")
                .currency("USD")
                .checkInDate(nextSunday.toString())
                .checkOutDate(checkOut.toString())
                .adults(1)
                .build()));

        String context = service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "need a cheap hotel in charlotte north carolina for sunday for one guest"
        );

        assertThat(context)
                .contains("Verified LiteAPI hotel offers for Charlotte, US")
                .contains("Charlotte Budget Inn")
                .contains("priceTotal=89.00");
        verify(liteApiHotelSearchService).search(
                "Charlotte",
                "US",
                nextSunday,
                checkOut,
                1,
                1,
                "USD",
                "US"
        );
        verifyNoInteractions(roomRepository);
    }

    @Test
    void pricingContextSearchesLiteApiForCharlotteNcWithTomorrowAndSinglePerson() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate checkOut = tomorrow.plusDays(1);
        when(liteApiHotelSearchService.search(
                any(String.class),
                any(String.class),
                any(LocalDate.class),
                any(LocalDate.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(String.class)
        )).thenReturn(List.of(ExternalHotelOfferDto.builder()
                .name("Charlotte Value Stay")
                .priceTotal("95.00")
                .currency("USD")
                .checkInDate(tomorrow.toString())
                .checkOutDate(checkOut.toString())
                .adults(1)
                .build()));

        String context = service.getVerifiedContext(
                ChatbotIntent.PRICING,
                """
                        user: charlotte, nc
                        user: i need a cheap hotel booking
                        Current user message: in Charlotte, North Carolina for a single person one room for one day tomorrow
                        """
        );

        assertThat(context)
                .contains("Verified LiteAPI hotel offers for Charlotte, US")
                .contains("Charlotte Value Stay")
                .contains("priceTotal=95.00");
        verify(liteApiHotelSearchService).search(
                "Charlotte",
                "US",
                tomorrow,
                checkOut,
                1,
                1,
                "USD",
                "US"
        );
        verifyNoInteractions(roomRepository);
    }

    @Test
    void amenitiesContextIncludesVerifiedHotelDetails() {
        when(hotelRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                hotel("Pool Resort", "Miami", "Outdoor pool and airport shuttle")
        )));

        String context = service.getVerifiedContext(ChatbotIntent.AMENITIES, "Does Pool Resort have a pool?");

        assertThat(context)
                .contains("Verified hotel details")
                .contains("hotel=Pool Resort")
                .contains("Outdoor pool and airport shuttle")
                .contains("Only mention amenities");
    }

    @Test
    void amenitiesContextMatchesHotelLocationWhenNameDoesNotMatch() {
        when(hotelRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                hotel("Transit Inn", "Miami Beach", "Airport shuttle")
        )));

        String context = service.getVerifiedContext(ChatbotIntent.AMENITIES, "amenities in miami beach");

        assertThat(context)
                .contains("hotel=Transit Inn")
                .contains("location=Miami Beach");
    }

    @Test
    void amenitiesContextMatchesHotelDescriptionWhenNameAndLocationDoNotMatch() {
        when(hotelRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                hotel("Quiet Inn", "Orlando", "Rooftop pool")
        )));

        String context = service.getVerifiedContext(ChatbotIntent.AMENITIES, "does it have a rooftop pool?");

        assertThat(context)
                .contains("hotel=Quiet Inn")
                .contains("Rooftop pool");
    }

    @Test
    void nonPublicDataIntentReturnsNoVerifiedContextWithoutRepositoryAccess() {
        String context = service.getVerifiedContext(ChatbotIntent.CANCELLATION_POLICY, "Can I refund booking 10?");

        assertThat(context).contains("No verified live hotel, room, pricing, booking, payment, or user-specific data");
        verifyNoInteractions(hotelRepository, roomRepository, liteApiHotelSearchService);
    }

    @Test
    void nullIntentReturnsNoVerifiedContextWithoutRepositoryAccess() {
        String context = service.getVerifiedContext(null, "Any rooms available?");

        assertThat(context).contains("No verified live hotel, room, pricing, booking, payment, or user-specific data");
        verifyNoInteractions(hotelRepository, roomRepository, liteApiHotelSearchService);
    }

    @Test
    void unknownIntentReturnsNoVerifiedContextWithoutRepositoryAccess() {
        String context = service.getVerifiedContext(ChatbotIntent.UNKNOWN, "Tell me a joke");

        assertThat(context).contains("No verified live hotel, room, pricing, booking, payment, or user-specific data");
        verifyNoInteractions(hotelRepository, roomRepository, liteApiHotelSearchService);
    }

    @Test
    void availabilityContextReturnsNoMatchMessageWhenRepositoryHasNoAvailableRooms() {
        when(roomRepository.findByAvailableTrue()).thenReturn(List.of());

        String context = service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "available rooms");

        assertThat(context)
                .contains("No verified available rooms matched the current message")
                .contains("Ask for destination or hotel");
        verifyNoInteractions(hotelRepository, liteApiHotelSearchService);
    }

    @Test
    void availabilityContextUsesBlankMessageFallbackAndLimitsRooms() {
        when(roomRepository.findByAvailableTrue()).thenReturn(List.of(
                room(1, RoomType.SINGLE, 80.0, true, "First", hotel("Hotel 1", "City", "Desc")),
                room(2, RoomType.DOUBLE, 90.0, true, "Second", hotel("Hotel 2", "City", "Desc")),
                room(3, RoomType.SUITE, 100.0, true, "Third", hotel("Hotel 3", "City", "Desc")),
                room(4, RoomType.SINGLE, 110.0, true, "Fourth", hotel("Hotel 4", "City", "Desc")),
                room(5, RoomType.DOUBLE, 120.0, true, "Fifth", hotel("Hotel 5", "City", "Desc")),
                room(6, RoomType.SUITE, 130.0, true, "Sixth", hotel("Hotel 6", "City", "Desc"))
        ));

        String context = service.getVerifiedContext(ChatbotIntent.AVAILABILITY, " ");

        assertThat(context)
                .contains("roomNumber=1")
                .contains("roomNumber=5")
                .doesNotContain("roomNumber=6");
    }

    @Test
    void availabilityContextFallsBackToFirstRoomsWhenMessageDoesNotMatchAnyRoom() {
        when(roomRepository.findByAvailableTrue()).thenReturn(List.of(
                room(301, RoomType.DOUBLE, 150.0, true, "Garden view", hotel("Garden Hotel", "Boston", "Quiet")),
                room(302, RoomType.SUITE, 250.0, true, "Skyline", hotel("Sky Hotel", "Seattle", "Tall"))
        ));

        String context = service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "available in Atlantis");

        assertThat(context)
                .contains("roomNumber=301")
                .contains("roomNumber=302");
    }

    @Test
    void availabilityContextMatchesRoomDescriptionLocationTypeAndRoomNumber() {
        Hotel hotel = hotel("Harbor Hotel", "Portland", "Waterfront");
        Room room = room(404, RoomType.SUITE, 220.0, true, "Corner balcony", hotel);
        when(roomRepository.findByAvailableTrue()).thenReturn(List.of(room));

        assertThat(service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "suite"))
                .contains("roomNumber=404");
        assertThat(service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "corner balcony"))
                .contains("roomNumber=404");
        assertThat(service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "harbor hotel"))
                .contains("roomNumber=404");
        assertThat(service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "portland"))
                .contains("roomNumber=404");
        assertThat(service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "404"))
                .contains("roomNumber=404");
    }

    @Test
    void availabilityContextUsesUnknownFallbacksForMissingRoomFields() {
        when(roomRepository.findByAvailableTrue()).thenReturn(List.of(
                room(null, null, null, true, " ", null)
        ));

        String context = service.getVerifiedContext(ChatbotIntent.AVAILABILITY, null);

        assertThat(context)
                .contains("roomNumber=unknown")
                .contains("type=unknown")
                .contains("pricePerNight=unknown")
                .contains("hotel=unknown")
                .contains("location=unknown")
                .contains("description=unknown");
    }

    @Test
    void availabilityContextEvaluatesNullRoomFieldsDuringMessageFiltering() {
        when(roomRepository.findByAvailableTrue()).thenReturn(List.of(
                room(null, null, null, true, null, hotel(null, null, null))
        ));

        String context = service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "available balcony");

        assertThat(context)
                .contains("roomNumber=unknown")
                .contains("hotel=unknown");
    }

    @Test
    void availabilityContextEvaluatesNullHotelDuringMessageFiltering() {
        when(roomRepository.findByAvailableTrue()).thenReturn(List.of(
                room(null, null, null, true, null, null)
        ));

        String context = service.getVerifiedContext(ChatbotIntent.AVAILABILITY, "available balcony");

        assertThat(context)
                .contains("roomNumber=unknown")
                .contains("hotel=unknown")
                .contains("location=unknown");
    }

    @Test
    void pricingContextReturnsNoPricesWhenRoomsHaveNoPrice() {
        when(roomRepository.findAll()).thenReturn(List.of(
                room(201, RoomType.SINGLE, null, true, "No price", hotel("No Price Hotel", "Paris", ""))
        ));

        String context = service.getVerifiedContext(ChatbotIntent.PRICING, "price for No Price Hotel");

        assertThat(context)
                .contains("No verified room prices matched the current message")
                .contains("Ask for destination or hotel");
    }

    @Test
    void pricingContextHandlesLiteApiEmptyResponse() {
        LocalDate checkIn = LocalDate.of(2026, 7, 10);
        LocalDate checkOut = LocalDate.of(2026, 7, 12);
        when(liteApiHotelSearchService.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        String context = service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "Find hotels in Austin, TX: from 2026-07-10 to 2026-07-12 for 2 adults"
        );

        assertThat(context)
                .contains("LiteAPI returned no verified hotel offers for Austin, TX")
                .contains(checkIn.toString())
                .contains(checkOut.toString())
                .contains("2 guest(s)");
        verifyNoInteractions(roomRepository);
    }

    @Test
    void pricingContextHandlesLiteApiException() {
        when(liteApiHotelSearchService.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("provider timeout"));

        String context = service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "Find hotels in Austin, TX: from 2026-07-10 to 2026-07-12 for two guests"
        );

        assertThat(context)
                .contains("LiteAPI hotel search could not be completed for Austin, TX")
                .contains("provider timeout");
        verifyNoInteractions(roomRepository);
    }

    @Test
    void pricingContextSortsLiteApiOffersByNumericPriceWithInvalidAndBlankPricesLast() {
        when(liteApiHotelSearchService.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        ExternalHotelOfferDto.builder().name("Blank Price").priceTotal(" ").build(),
                        ExternalHotelOfferDto.builder().name("Invalid Price").priceTotal("not-a-number").build(),
                        ExternalHotelOfferDto.builder().name("Fifty").priceTotal("50.00").build(),
                        ExternalHotelOfferDto.builder().name("Ten").priceTotal("10.00").build()
                ));

        String context = service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "Find hotels in Austin, TX: from 2026-07-10 to 2026-07-12 for three people"
        );

        assertThat(context)
                .contains("hotel=Ten")
                .contains("hotel=Fifty")
                .contains("hotel=Blank Price")
                .contains("priceTotal=unknown");
        assertThat(context.indexOf("hotel=Ten")).isLessThan(context.indexOf("hotel=Fifty"));
        assertThat(context.indexOf("hotel=Fifty")).isLessThan(context.indexOf("hotel=Blank Price"));
    }

    @Test
    void pricingContextDefaultsCheckoutToNextDayWhenSecondDateIsNotAfterCheckIn() {
        LocalDate checkIn = LocalDate.of(2026, 7, 10);
        when(liteApiHotelSearchService.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "Find hotels in Austin, TX: from 2026-07-10 to 2026-07-10 for four guests"
        );

        verify(liteApiHotelSearchService).search(
                "Austin",
                "TX",
                checkIn,
                checkIn.plusDays(1),
                4,
                1,
                "USD",
                "US"
        );
    }

    @Test
    void pricingContextParsesGenericTwoLetterCountryCodeAndDigitGuestCount() {
        LocalDate checkIn = LocalDate.of(2026, 8, 1);
        LocalDate checkOut = LocalDate.of(2026, 8, 3);
        when(liteApiHotelSearchService.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "Find hotels near Paris, FR: from 2026-08-01 to 2026-08-03 for 6 adults"
        );

        verify(liteApiHotelSearchService).search(
                "Paris",
                "FR",
                checkIn,
                checkOut,
                6,
                1,
                "USD",
                "US"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "united states",
            "usa",
            "us"
    })
    void countryCodeMapperParsesUnitedStatesRegionAliases(String region) throws Exception {
        Method countryCodeForRegion = ChatbotContextServiceImpl.class
                .getDeclaredMethod("countryCodeForRegion", String.class);
        countryCodeForRegion.setAccessible(true);

        assertThat(countryCodeForRegion.invoke(service, region)).isEqualTo("US");
    }

    @ParameterizedTest
    @CsvSource({
            "sunday,SUNDAY",
            "monday,MONDAY",
            "tuesday,TUESDAY",
            "wednesday,WEDNESDAY",
            "thursday,THURSDAY",
            "friday,FRIDAY",
            "saturday,SATURDAY"
    })
    void pricingContextParsesWeekdayCheckInDates(String dayName, DayOfWeek dayOfWeek) {
        LocalDate expectedCheckIn = LocalDate.now().with(TemporalAdjusters.nextOrSame(dayOfWeek));
        when(liteApiHotelSearchService.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "Find hotels in Austin, TX: for " + dayName + " for five adults"
        );

        verify(liteApiHotelSearchService).search(
                eq("Austin"),
                eq("TX"),
                eq(expectedCheckIn),
                eq(expectedCheckIn.plusDays(1)),
                eq(5),
                eq(1),
                eq("USD"),
                eq("US")
        );
    }

    @ParameterizedTest
    @CsvSource({
            "one guest,1",
            "two adults,2",
            "three people,3",
            "four people,4",
            "five guests,5",
            "six adults,6",
            "single person,1"
    })
    void pricingContextParsesSupportedGuestWords(String guestPhrase, int adults) {
        LocalDate checkIn = LocalDate.of(2026, 9, 1);
        when(liteApiHotelSearchService.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "Find hotels in Austin, TX: on 2026-09-01 for " + guestPhrase
        );

        verify(liteApiHotelSearchService).search(
                eq("Austin"),
                eq("TX"),
                eq(checkIn),
                eq(checkIn.plusDays(1)),
                eq(adults),
                eq(1),
                eq("USD"),
                eq("US")
        );
    }

    @Test
    void pricingContextReportsMissingDestinationWhenDateExistsButRegionIsUnsupported() {
        String context = service.getVerifiedContext(
                ChatbotIntent.PRICING,
                "Find hotels in Atlantis, ocean on 2026-09-01 for two guests"
        );

        assertThat(context)
                .contains("Missing destination or hotel")
                .doesNotContain("Missing check-in date")
                .doesNotContain("Missing guest count");
        verifyNoInteractions(roomRepository, liteApiHotelSearchService);
    }

    @Test
    void pricingContextUsesDatabaseFallbackWhenNoSearchCriteriaArePresent() {
        Hotel hotel = hotel("Budget Hotel", "Dallas", "Downtown");
        when(roomRepository.findAll()).thenReturn(List.of(
                room(10, RoomType.SINGLE, 70.0, true, "Compact", hotel)
        ));

        String context = service.getVerifiedContext(ChatbotIntent.PRICING, "cheap compact room");

        assertThat(context)
                .contains("Verified room prices from the application database")
                .contains("roomNumber=10")
                .contains("pricePerNight=70.0");
        verifyNoInteractions(liteApiHotelSearchService);
    }

    @Test
    void amenitiesContextReturnsNoMatchMessageWhenNoHotelsExist() {
        when(hotelRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        String context = service.getVerifiedContext(ChatbotIntent.AMENITIES, "pool?");

        assertThat(context)
                .contains("No verified hotel amenity details matched the current message")
                .contains("Ask the user to choose a hotel");
        verifyNoInteractions(roomRepository, liteApiHotelSearchService);
    }

    @Test
    void amenitiesContextUsesBlankMessageAndUnknownHotelFieldFallbacks() {
        when(hotelRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                hotel(null, " ", null)
        )));

        String context = service.getVerifiedContext(ChatbotIntent.AMENITIES, "\n");

        assertThat(context)
                .contains("hotel=unknown")
                .contains("location=unknown")
                .contains("description=unknown");
    }

    @Test
    void amenitiesContextFallsBackToFetchedHotelsWhenMessageDoesNotMatch() {
        when(hotelRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                hotel("Mountain Lodge", "Denver", "Ski shuttle")
        )));

        String context = service.getVerifiedContext(ChatbotIntent.AMENITIES, "beachfront breakfast");

        assertThat(context)
                .contains("hotel=Mountain Lodge")
                .contains("Ski shuttle");
    }

    @Test
    void defensiveStringHelpersHandleNullAndBlankValues() throws Exception {
        Method cleanLocation = ChatbotContextServiceImpl.class.getDeclaredMethod("cleanLocation", String.class);
        Method toTitleCase = ChatbotContextServiceImpl.class.getDeclaredMethod("toTitleCase", String.class);
        cleanLocation.setAccessible(true);
        toTitleCase.setAccessible(true);

        assertThat(cleanLocation.invoke(service, new Object[]{null})).isEqualTo("");
        assertThat(toTitleCase.invoke(service, " ")).isEqualTo("");
    }

    private Hotel hotel(String name, String location, String description) {
        return Hotel.builder()
                .name(name)
                .location(location)
                .description(description)
                .build();
    }

    private Room room(Integer roomNumber, RoomType type, Double price, boolean available, String description, Hotel hotel) {
        return Room.builder()
                .roomNumber(roomNumber)
                .type(type)
                .pricePerNight(price)
                .available(available)
                .description(description)
                .hotel(hotel)
                .build();
    }
}
