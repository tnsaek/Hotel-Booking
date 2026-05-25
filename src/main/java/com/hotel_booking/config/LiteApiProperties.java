package com.hotel_booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "liteapi")
public class LiteApiProperties {
    private String baseUrl = "https://api.liteapi.travel/v3.0";
    private String apiKey;
    private int limit = 20;
    private int timeoutSeconds = 8;
}
