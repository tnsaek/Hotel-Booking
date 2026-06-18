package com.hotel_booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {
    private String apiKey;
    private String model = "gemini-2.5-flash";

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }
}
