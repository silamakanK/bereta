package com.bereta.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** SMS transactionnels Brevo : POST /v3/transactionalSMS/sms. */
@Component
@ConditionalOnProperty(prefix = "bereta.sms", name = "provider", havingValue = "brevo")
public class BrevoSmsSender implements SmsSender {

    private final RestClient client;
    private final String sender;

    public BrevoSmsSender(@Value("${bereta.sms.brevo.api-key:}") String apiKey,
                          @Value("${bereta.sms.sender:BERETA}") String sender,
                          @Value("${bereta.sms.brevo.base-url:https://api.brevo.com}") String baseUrl) {
        if (apiKey.isBlank()) throw new IllegalStateException("BREVO_API_KEY doit être défini quand BERETA_SMS_PROVIDER=brevo.");
        this.client = RestClient.builder().baseUrl(baseUrl).defaultHeader("api-key", apiKey).build();
        this.sender = sender;
    }

    @Override
    public void send(String phoneE164, String message) {
        client.post().uri("/v3/transactionalSMS/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sender", sender, "recipient", phoneE164.replace("+", ""),
                        "content", message, "type", "transactional"))
                .retrieve().toBodilessEntity();
    }
}
