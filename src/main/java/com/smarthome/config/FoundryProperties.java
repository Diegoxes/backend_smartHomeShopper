package com.smarthome.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "azure.ai.foundry")
public class FoundryProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String deployment = "gpt-4o";
    private double temperature = 0.2;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && deployment != null && !deployment.isBlank();
    }
}
