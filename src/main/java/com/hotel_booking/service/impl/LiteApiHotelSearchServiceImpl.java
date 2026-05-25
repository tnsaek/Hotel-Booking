package com.hotel_booking.service.impl;

import com.hotel_booking.config.LiteApiProperties;
import com.hotel_booking.dto.ExternalHotelOfferDto;
import com.hotel_booking.service.LiteApiHotelSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LiteApiHotelSearchServiceImpl implements LiteApiHotelSearchService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final LiteApiProperties properties;

    private final RestClient restClient = createRestClient();

    @Override
    public List<ExternalHotelOfferDto> search(
            String cityName,
            String countryCode,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Integer adults,
            Integer roomQuantity,
            String currency,
            String guestNationality
    ) {
        validateCredentials();
        validateSearch(cityName, countryCode, checkInDate, checkOutDate, adults, roomQuantity, guestNationality);

        String normalizedCityName = cityName.trim();
        String normalizedCountryCode = countryCode.trim().toUpperCase(Locale.ROOT);

        try {
            Map<String, Object> hotelResponse = restClient.get()
                    .uri(properties.getBaseUrl()
                                    + "/data/hotels?cityName={cityName}&countryCode={countryCode}&limit={limit}",
                            normalizedCityName,
                            normalizedCountryCode,
                            properties.getLimit())
                    .header("X-API-Key", properties.getApiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responses) -> {
                        throw new IllegalStateException("LiteAPI hotel search failed with status " + responses.getStatusCode());
                    })
                    .body(MAP_RESPONSE);
            Map<String, ExternalHotelOfferDto> hotelSummaries = indexByHotelId(mapHotels(
                    hotelResponse,
                    normalizedCityName + ", " + normalizedCountryCode,
                    checkInDate,
                    checkOutDate,
                    adults,
                    roomQuantity
            ));

            Map<String, Object> ratesResponse = restClient.post()
                    .uri(properties.getBaseUrl() + "/hotels/rates")
                    .header("X-API-Key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(
                            normalizedCityName,
                            normalizedCountryCode,
                            checkInDate,
                            checkOutDate,
                            adults,
                            roomQuantity,
                            normalizeCurrency(currency),
                            guestNationality.trim().toUpperCase(Locale.ROOT)
                    ))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responses) -> {
                        throw new IllegalStateException("LiteAPI search failed with status " + responses.getStatusCode());
                    })
                    .body(MAP_RESPONSE);

            return mapOffers(
                    ratesResponse,
                    hotelSummaries,
                    normalizedCityName + ", " + normalizedCountryCode,
                    checkInDate,
                    checkOutDate,
                    adults,
                    roomQuantity
            );
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
                throw exception;
            }
            throw new IllegalStateException("LiteAPI search request failed or timed out");
        }
    }

    private List<ExternalHotelOfferDto> mapHotels(
            Map<String, Object> response,
            String cityCode,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Integer adults,
            Integer roomQuantity
    ) {
        List<Map<String, Object>> data = listOfMaps(response == null ? null : response.get("data"));
        if (data.isEmpty()) {
            data = listOfMaps(response == null ? null : response.get("hotels"));
        }
        if (data.isEmpty()) {
            return List.of();
        }

        List<ExternalHotelOfferDto> results = new ArrayList<>();
        data.forEach(item -> results.add(ExternalHotelOfferDto.builder()
                .provider("LiteAPI")
                .hotelId(firstText(item, item, "hotelId", "id"))
                .name(firstText(item, item, "name", "hotelName"))
                .cityCode(cityCode)
                .address(firstText(item, item, "address", "addressLine", "address1", "location"))
                .latitude(firstDouble(item, item, "latitude", "lat"))
                .longitude(firstDouble(item, item, "longitude", "lng", "lon"))
                .checkInDate(checkInDate.toString())
                .checkOutDate(checkOutDate.toString())
                .adults(adults)
                .roomQuantity(roomQuantity)
                .description(firstText(item, item, "description", "shortDescription"))
                .build()));

        return results;
    }

    private RestClient createRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private Map<String, Object> buildRequestBody(
            String cityName,
            String countryCode,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Integer adults,
            Integer roomQuantity,
            String currency,
            String guestNationality
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cityName", cityName);
        body.put("countryCode", countryCode);
        body.put("checkin", checkInDate.toString());
        body.put("checkout", checkOutDate.toString());
        body.put("currency", currency);
        body.put("guestNationality", guestNationality);
        body.put("occupancies", buildOccupancies(adults, roomQuantity));
        body.put("maxRatesPerHotel", 1);
        body.put("limit", properties.getLimit());
        body.put("timeout", properties.getTimeoutSeconds());
        body.put("includeHotelData", true);
        return body;
    }

    private List<Map<String, Object>> buildOccupancies(Integer adults, Integer roomQuantity) {
        List<Map<String, Object>> occupancies = new ArrayList<>();
        int baseAdultsPerRoom = adults / roomQuantity;
        int roomsWithExtraAdult = adults % roomQuantity;

        for (int room = 0; room < roomQuantity; room++) {
            int roomAdults = baseAdultsPerRoom + (room < roomsWithExtraAdult ? 1 : 0);
            occupancies.add(Map.of("adults", Math.max(roomAdults, 1)));
        }

        return occupancies;
    }

    private List<ExternalHotelOfferDto> mapOffers(
            Map<String, Object> response,
            Map<String, ExternalHotelOfferDto> hotelSummaries,
            String cityCode,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Integer adults,
            Integer roomQuantity
    ) {
        List<Map<String, Object>> data = listOfMaps(response == null ? null : response.get("data"));
        if (data.isEmpty()) {
            return List.of();
        }

        List<ExternalHotelOfferDto> results = new ArrayList<>();
        data.forEach(item -> {
            Map<String, Object> hotel = firstObject(item, "hotel", "hotelData", "hotelDetails");
            Map<String, Object> roomType = firstArrayItem(item.get("roomTypes"));
            Map<String, Object> rate = roomType == null ? null : firstArrayItem(roomType.get("rates"));
            Map<String, Object> price = firstObject(roomType, "offerRetailRate", "suggestedSellingPrice", "offerInitialPrice");
            String hotelId = firstText(item, hotel, "hotelId", "id");
            ExternalHotelOfferDto hotelSummary = hotelSummaries.get(hotelId);

            if (price == null && rate != null) {
                Map<String, Object> retailRate = object(rate.get("retailRate")).orElse(null);
                price = retailRate == null ? null : firstArrayItem(retailRate.get("total"));
            }

            results.add(ExternalHotelOfferDto.builder()
                    .provider("LiteAPI")
                    .hotelId(hotelId)
                    .name(firstText(
                            firstText(item, hotel, "name", "hotelName"),
                            hotelSummary == null ? null : hotelSummary.getName()
                    ))
                    .cityCode(cityCode)
                    .address(firstText(
                            firstText(item, hotel, "address", "addressLine", "address1", "location"),
                            hotelSummary == null ? null : hotelSummary.getAddress()
                    ))
                    .latitude(firstDouble(firstDouble(item, hotel, "latitude", "lat"),
                            hotelSummary == null ? null : hotelSummary.getLatitude()))
                    .longitude(firstDouble(firstDouble(item, hotel, "longitude", "lng", "lon"),
                            hotelSummary == null ? null : hotelSummary.getLongitude()))
                    .offerId(roomType == null ? null : text(roomType, "offerId"))
                    .checkInDate(checkInDate.toString())
                    .checkOutDate(checkOutDate.toString())
                    .adults(adults)
                    .roomQuantity(roomQuantity)
                    .roomType(firstText(rate, roomType, "name", "roomName", "roomTypeName"))
                    .description(rate == null ? null : firstText(rate, rate, "boardName", "remarks"))
                    .priceTotal(price == null ? null : text(price, "amount"))
                    .currency(price == null ? null : text(price, "currency"))
                    .build());
        });

        return results;
    }

    private Map<String, ExternalHotelOfferDto> indexByHotelId(List<ExternalHotelOfferDto> hotels) {
        Map<String, ExternalHotelOfferDto> indexedHotels = new LinkedHashMap<>();
        hotels.forEach(hotel -> {
            if (StringUtils.hasText(hotel.getHotelId())) {
                indexedHotels.put(hotel.getHotelId(), hotel);
            }
        });
        return indexedHotels;
    }

    private Map<String, Object> firstArrayItem(Object value) {
        List<Map<String, Object>> values = listOfMaps(value);
        return values.isEmpty() ? null : values.get(0);
    }

    private Map<String, Object> firstObject(Map<String, Object> node, String... fieldNames) {
        if (node == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            Optional<Map<String, Object>> value = object(node.get(fieldName));
            if (value.isPresent()) {
                return value.get();
            }
        }
        return null;
    }

    private String firstText(Map<String, Object> primary, Map<String, Object> secondary, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(primary, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
            value = text(secondary, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Map<String, Object> node, String fieldName) {
        if (node == null) {
            return null;
        }
        Object rawValue = node.get(fieldName);
        String value = rawValue == null ? null : String.valueOf(rawValue);
        return StringUtils.hasText(value) ? value : null;
    }

    private Double firstDouble(Map<String, Object> primary, Map<String, Object> secondary, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Double value = number(primary, fieldName);
            if (value != null) {
                return value;
            }
            value = number(secondary, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double firstDouble(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double number(Map<String, Object> node, String fieldName) {
        if (node == null || !(node.get(fieldName) instanceof Number number)) {
            return null;
        }
        return number.doubleValue();
    }

    private String normalizeCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase(Locale.ROOT) : "USD";
    }

    private Optional<Map<String, Object>> object(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Optional.empty();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> {
            if (key != null) {
                map.put(String.valueOf(key), mapValue);
            }
        });
        return Optional.of(map);
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }

        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object item : rawList) {
            object(item).ifPresent(maps::add);
        }
        return maps;
    }

    private void validateCredentials() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("LiteAPI key is not configured");
        }
    }

    private void validateSearch(
            String cityName,
            String countryCode,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Integer adults,
            Integer roomQuantity,
            String guestNationality
    ) {
        if (!StringUtils.hasText(cityName)) {
            throw new IllegalArgumentException("cityName is required");
        }
        if (!StringUtils.hasText(countryCode) || countryCode.trim().length() != 2) {
            throw new IllegalArgumentException("countryCode must be a 2-letter country code");
        }
        if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("checkOutDate must be after checkInDate");
        }
        if (adults == null || adults < 1) {
            throw new IllegalArgumentException("adults must be at least 1");
        }
        if (roomQuantity == null || roomQuantity < 1) {
            throw new IllegalArgumentException("roomQuantity must be at least 1");
        }
        if (!StringUtils.hasText(guestNationality) || guestNationality.trim().length() != 2) {
            throw new IllegalArgumentException("guestNationality must be a 2-letter country code");
        }
    }
}
