package com.smarthome.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "twilio")
public class TwilioProperties {

    private String accountSid = "";
    private String authToken = "";
    private String whatsappFrom = "whatsapp:+14155238886";

    public boolean hasMediaCredentials() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank();
    }
}
