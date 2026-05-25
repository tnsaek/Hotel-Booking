package com.hotel_booking.service.impl;

import com.hotel_booking.config.LiteApiProperties;
import com.hotel_booking.dto.ExternalHotelOfferDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiteApiHotelSearchServiceImplTest {

    private HttpServer server;
    private LiteApiProperties properties;
    private LiteApiHotelSearchServiceImpl service;
    private AtomicReference<String> ratesRequestBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        properties = new LiteApiProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setApiKey("test-api-key");
        properties.setLimit(20);
        properties.setTimeoutSeconds(8);

        service = new LiteApiHotelSearchServiceImpl(properties);
        ratesRequestBody = new AtomicReference<>("");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void searchCombinesHotelDataWithRateData() {
        respondJson("/data/hotels", """
                {
                  "data": [
                    {
                      "hotelId": "hotel-1",
                      "name": "Motto by Hilton New York City Chelsea",
                      "address": "113 West 24th Street",
                      "latitude": 40.744,
                      "longitude": -73.993,
                      "description": "Central hotel"
                    }
                  ]
                }
                """);
        respondJson("/hotels/rates", exchange -> {
            ratesRequestBody.set(readBody(exchange));
            writeJson(exchange, 200, """
                    {
                      "data": [
                        {
                          "hotelId": "hotel-1",
                          "roomTypes": [
                            {
                              "offerId": "offer-token",
                              "rates": [
                                {
                                  "name": "1 KING BED",
                                  "boardName": "Room Only",
                                  "retailRate": {
                                    "total": [
                                      { "amount": "217.76", "currency": "USD" }
                                    ]
                                  }
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """);
        });

        List<ExternalHotelOfferDto> response = service.search(
                " new york ",
                "us",
                LocalDate.of(2026, 5, 29),
                LocalDate.of(2026, 5, 30),
                2,
                1,
                "usd",
                "us"
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst())
                .extracting(
                        ExternalHotelOfferDto::getProvider,
                        ExternalHotelOfferDto::getHotelId,
                        ExternalHotelOfferDto::getName,
                        ExternalHotelOfferDto::getCityCode,
                        ExternalHotelOfferDto::getAddress,
                        ExternalHotelOfferDto::getRoomType,
                        ExternalHotelOfferDto::getDescription,
                        ExternalHotelOfferDto::getPriceTotal,
                        ExternalHotelOfferDto::getCurrency,
                        ExternalHotelOfferDto::getOfferId,
                        ExternalHotelOfferDto::getAdults,
                        ExternalHotelOfferDto::getRoomQuantity
                )
                .containsExactly(
                        "LiteAPI",
                        "hotel-1",
                        "Motto by Hilton New York City Chelsea",
                        "new york, US",
                        "113 West 24th Street",
                        "1 KING BED",
                        "Room Only",
                        "217.76",
                        "USD",
                        "offer-token",
                        2,
                        1
                );
        assertThat(response.getFirst().getLatitude()).isEqualTo(40.744);
        assertThat(response.getFirst().getLongitude()).isEqualTo(-73.993);
        assertThat(ratesRequestBody.get())
                .contains("\"cityName\":\"new york\"")
                .contains("\"countryCode\":\"US\"")
                .contains("\"currency\":\"USD\"")
                .contains("\"guestNationality\":\"US\"")
                .contains("\"occupancies\":[{\"adults\":2}]")
                .contains("\"includeHotelData\":true");
    }

    @Test
    void searchUsesEmbeddedRateHotelDataWhenHotelSummaryIsMissing() {
        respondJson("/data/hotels", "{ \"hotels\": [] }");
        respondJson("/hotels/rates", """
                {
                  "data": [
                    {
                      "hotelData": {
                        "id": "hotel-2",
                        "hotelName": "Arlo Midtown",
                        "location": "351 West 38th Street",
                        "lat": 40.756,
                        "lon": -73.993
                      },
                      "roomTypes": [
                        {
                          "offerId": "offer-2",
                          "roomName": "Deluxe King",
                          "offerRetailRate": { "amount": "299.26", "currency": "USD" },
                          "rates": [
                            { "remarks": "Breakfast Included" }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);

        List<ExternalHotelOfferDto> response = service.search(
                "New York",
                "US",
                LocalDate.of(2026, 5, 29),
                LocalDate.of(2026, 5, 30),
                1,
                1,
                "",
                "US"
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst())
                .extracting(
                        ExternalHotelOfferDto::getHotelId,
                        ExternalHotelOfferDto::getName,
                        ExternalHotelOfferDto::getAddress,
                        ExternalHotelOfferDto::getRoomType,
                        ExternalHotelOfferDto::getDescription,
                        ExternalHotelOfferDto::getPriceTotal,
                        ExternalHotelOfferDto::getCurrency
                )
                .containsExactly(
                        "hotel-2",
                        "Arlo Midtown",
                        "351 West 38th Street",
                        "Deluxe King",
                        "Breakfast Included",
                        "299.26",
                        "USD"
                );
    }

    @Test
    void searchReturnsEmptyListWhenRatesHaveNoData() {
        respondJson("/data/hotels", "{ \"data\": [] }");
        respondJson("/hotels/rates", "{ \"data\": [] }");

        List<ExternalHotelOfferDto> response = service.search(
                "Paris",
                "FR",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                1,
                1,
                "EUR",
                "FR"
        );

        assertThat(response).isEmpty();
    }

    @Test
    void searchBuildsDistributedOccupanciesForMultipleRooms() {
        respondJson("/data/hotels", """
                {
                  "data": [
                    { "hotelId": "hotel-1", "name": "Hotel One" }
                  ]
                }
                """);
        respondJson("/hotels/rates", exchange -> {
            ratesRequestBody.set(readBody(exchange));
            writeJson(exchange, 200, "{ \"data\": [] }");
        });

        List<ExternalHotelOfferDto> response = service.search(
                "Paris",
                "FR",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                3,
                2,
                "EUR",
                "FR"
        );

        assertThat(response).isEmpty();
        assertThat(ratesRequestBody.get()).contains("\"occupancies\":[{\"adults\":2},{\"adults\":1}]");
    }

    @Test
    void searchMapsOfferWithNoRoomTypeOrPriceAsSparseResult() {
        respondJson("/data/hotels", """
                {
                  "data": [
                    {
                      "hotelId": "hotel-1",
                      "name": "Summary Hotel",
                      "address": "Summary Address",
                      "latitude": 40.1,
                      "longitude": -73.1
                    }
                  ]
                }
                """);
        respondJson("/hotels/rates", """
                {
                  "data": [
                    { "hotelId": "hotel-1" }
                  ]
                }
                """);

        List<ExternalHotelOfferDto> response = service.search(
                "Paris",
                "FR",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                1,
                1,
                "EUR",
                "FR"
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst())
                .extracting(
                        ExternalHotelOfferDto::getHotelId,
                        ExternalHotelOfferDto::getName,
                        ExternalHotelOfferDto::getAddress,
                        ExternalHotelOfferDto::getOfferId,
                        ExternalHotelOfferDto::getRoomType,
                        ExternalHotelOfferDto::getDescription,
                        ExternalHotelOfferDto::getPriceTotal,
                        ExternalHotelOfferDto::getCurrency
                )
                .containsExactly(
                        "hotel-1",
                        "Summary Hotel",
                        "Summary Address",
                        null,
                        null,
                        null,
                        null,
                        null
                );
    }

    @Test
    void searchMapsRateWithoutRetailRateAsOfferWithoutPrice() {
        respondJson("/data/hotels", """
                {
                  "data": [
                    { "hotelId": "hotel-1", "name": "Summary Hotel" }
                  ]
                }
                """);
        respondJson("/hotels/rates", """
                {
                  "data": [
                    {
                      "hotelId": "hotel-1",
                      "roomTypes": [
                        {
                          "offerId": "offer-without-price",
                          "rates": [
                            { "name": "Standard Room" }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);

        List<ExternalHotelOfferDto> response = service.search(
                "Paris",
                "FR",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                1,
                1,
                "EUR",
                "FR"
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst())
                .extracting(
                        ExternalHotelOfferDto::getOfferId,
                        ExternalHotelOfferDto::getRoomType,
                        ExternalHotelOfferDto::getPriceTotal,
                        ExternalHotelOfferDto::getCurrency
                )
                .containsExactly("offer-without-price", "Standard Room", null, null);
    }

    @Test
    void searchMapsHotelSummaryFallbackFieldsAndIgnoresBlankHotelIds() {
        respondJson("/data/hotels", """
                {
                  "hotels": [
                    {
                      "id": "hotel-1",
                      "hotelName": "Fallback Hotel",
                      "addressLine": "Fallback Address",
                      "lat": 40.2,
                      "lng": -73.2,
                      "shortDescription": "Fallback description"
                    },
                    {
                      "hotelId": " ",
                      "name": "No Id Hotel"
                    }
                  ]
                }
                """);
        respondJson("/hotels/rates", """
                {
                  "data": [
                    {
                      "hotelId": "hotel-1",
                      "roomTypes": [
                        {
                          "offerId": "offer-1",
                          "roomTypeName": "Suite",
                          "suggestedSellingPrice": { "amount": "410.00", "currency": "EUR" },
                          "rates": [
                            { "boardName": "Half Board" }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);

        List<ExternalHotelOfferDto> response = service.search(
                "Paris",
                "FR",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                1,
                1,
                "EUR",
                "FR"
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst())
                .extracting(
                        ExternalHotelOfferDto::getName,
                        ExternalHotelOfferDto::getAddress,
                        ExternalHotelOfferDto::getLatitude,
                        ExternalHotelOfferDto::getLongitude,
                        ExternalHotelOfferDto::getRoomType,
                        ExternalHotelOfferDto::getDescription,
                        ExternalHotelOfferDto::getPriceTotal,
                        ExternalHotelOfferDto::getCurrency
                )
                .containsExactly(
                        "Fallback Hotel",
                        "Fallback Address",
                        40.2,
                        -73.2,
                        "Suite",
                        "Half Board",
                        "410.00",
                        "EUR"
                );
    }

    @Test
    void searchRejectsInvalidConfigurationAndCriteria() {
        properties.setApiKey("");
        assertThatThrownBy(() -> validSearch())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LiteAPI key is not configured");

        properties.setApiKey("test-api-key");
        assertThatThrownBy(() -> service.search("", "US", LocalDate.now(), LocalDate.now().plusDays(1), 1, 1, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cityName is required");
        assertThatThrownBy(() -> service.search("Paris", "", LocalDate.now(), LocalDate.now().plusDays(1), 1, 1, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("countryCode must be a 2-letter country code");
        assertThatThrownBy(() -> service.search("Paris", "USA", LocalDate.now(), LocalDate.now().plusDays(1), 1, 1, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("countryCode must be a 2-letter country code");
        assertThatThrownBy(() -> service.search("Paris", "FR", null, LocalDate.now().plusDays(1), 1, 1, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("checkOutDate must be after checkInDate");
        assertThatThrownBy(() -> service.search("Paris", "FR", LocalDate.now(), null, 1, 1, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("checkOutDate must be after checkInDate");
        assertThatThrownBy(() -> service.search("Paris", "FR", LocalDate.now(), LocalDate.now(), 1, 1, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("checkOutDate must be after checkInDate");
        assertThatThrownBy(() -> service.search("Paris", "FR", LocalDate.now(), LocalDate.now().plusDays(1), null, 1, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("adults must be at least 1");
        assertThatThrownBy(() -> service.search("Paris", "FR", LocalDate.now(), LocalDate.now().plusDays(1), 0, 1, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("adults must be at least 1");
        assertThatThrownBy(() -> service.search("Paris", "FR", LocalDate.now(), LocalDate.now().plusDays(1), 1, null, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("roomQuantity must be at least 1");
        assertThatThrownBy(() -> service.search("Paris", "FR", LocalDate.now(), LocalDate.now().plusDays(1), 1, 0, "USD", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("roomQuantity must be at least 1");
        assertThatThrownBy(() -> service.search("Paris", "FR", LocalDate.now(), LocalDate.now().plusDays(1), 1, 1, "USD", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("guestNationality must be a 2-letter country code");
        assertThatThrownBy(() -> service.search("Paris", "FR", LocalDate.now(), LocalDate.now().plusDays(1), 1, 1, "USD", "USA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("guestNationality must be a 2-letter country code");
    }

    @Test
    void searchThrowsWhenLiteApiReturnsAnError() {
        respondJson("/data/hotels", 503, "{ \"message\": \"down\" }");

        assertThatThrownBy(() -> validSearch())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LiteAPI hotel search failed with status 503 SERVICE_UNAVAILABLE");
    }

    @Test
    void searchThrowsWhenLiteApiRatesReturnsAnError() {
        respondJson("/data/hotels", "{ \"data\": [] }");
        respondJson("/hotels/rates", 503, "{ \"message\": \"down\" }");

        assertThatThrownBy(() -> validSearch())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LiteAPI search failed with status 503 SERVICE_UNAVAILABLE");
    }

    @Test
    void searchWrapsTransportFailures() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        properties.setBaseUrl("http://localhost:" + unusedPort);
        service = new LiteApiHotelSearchServiceImpl(properties);

        assertThatThrownBy(() -> validSearch())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LiteAPI search request failed or timed out");
    }

    @Test
    void searchRethrowsIllegalArgumentExceptionFromRequestCreation() {
        properties.setBaseUrl("http://[invalid-host");
        service = new LiteApiHotelSearchServiceImpl(properties);

        assertThatThrownBy(() -> validSearch())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void privateMappingHelpersHandleNullAndEmptyInputs() throws Exception {
        assertThat(invokeMapHotels(null)).isEmpty();
        assertThat(invokeMapOffers(null, Map.of())).isEmpty();

        Method firstText = LiteApiHotelSearchServiceImpl.class.getDeclaredMethod("firstText", String[].class);
        firstText.setAccessible(true);
        assertThat(firstText.invoke(service, (Object) new String[]{null, " ", ""})).isNull();

        Method firstDouble = LiteApiHotelSearchServiceImpl.class.getDeclaredMethod("firstDouble", Double[].class);
        firstDouble.setAccessible(true);
        assertThat(firstDouble.invoke(service, (Object) new Double[]{null, null})).isNull();

        Method object = LiteApiHotelSearchServiceImpl.class.getDeclaredMethod("object", Object.class);
        object.setAccessible(true);
        Map<Object, Object> rawMap = new LinkedHashMap<>();
        rawMap.put(null, "ignored");
        rawMap.put("name", "Hotel");

        Object optional = object.invoke(service, rawMap);

        assertThat(String.valueOf(optional)).contains("name=Hotel").doesNotContain("ignored");
    }

    @SuppressWarnings("unchecked")
    private List<ExternalHotelOfferDto> invokeMapHotels(Map<String, Object> response) throws Exception {
        Method mapHotels = LiteApiHotelSearchServiceImpl.class.getDeclaredMethod(
                "mapHotels",
                Map.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                Integer.class,
                Integer.class
        );
        mapHotels.setAccessible(true);
        return (List<ExternalHotelOfferDto>) mapHotels.invoke(
                service,
                response,
                "Paris, FR",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                1,
                1
        );
    }

    @SuppressWarnings("unchecked")
    private List<ExternalHotelOfferDto> invokeMapOffers(
            Map<String, Object> response,
            Map<String, ExternalHotelOfferDto> hotelSummaries
    ) throws Exception {
        Method mapOffers = LiteApiHotelSearchServiceImpl.class.getDeclaredMethod(
                "mapOffers",
                Map.class,
                Map.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                Integer.class,
                Integer.class
        );
        mapOffers.setAccessible(true);
        return (List<ExternalHotelOfferDto>) mapOffers.invoke(
                service,
                response,
                hotelSummaries,
                "Paris, FR",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                1,
                1
        );
    }

    private List<ExternalHotelOfferDto> validSearch() {
        return service.search(
                "Paris",
                "FR",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                1,
                1,
                "EUR",
                "FR"
        );
    }

    private void respondJson(String path, String json) {
        respondJson(path, 200, json);
    }

    private void respondJson(String path, int status, String json) {
        respondJson(path, exchange -> writeJson(exchange, status, json));
    }

    private void respondJson(String path, ExchangeHandler handler) {
        server.createContext(path, exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-API-Key")).isEqualTo("test-api-key");
            handler.handle(exchange);
        });
    }

    private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
