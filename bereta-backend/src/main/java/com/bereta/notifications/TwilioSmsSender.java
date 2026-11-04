package com.bereta.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** SMS Twilio : POST /2010-04-01/Accounts/{sid}/Messages.json. */
@Component
@ConditionalOnProperty(prefix = "bereta.sms", name = "provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {

    private final RestClient client;
    private final String accountSid;
    private final String from;

    public TwilioSmsSender(@Value("${bereta.sms.twilio.account-sid:}") String accountSid,
                           @Value("${bereta.sms.twilio.auth-token:}") String authToken,
                           @Value("${bereta.sms.twilio.from:}") String from) {
        if (accountSid.isBlank() || authToken.isBlank() || from.isBlank()) {
            throw new IllegalStateException("TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN et TWILIO_FROM doivent être définis.");
        }
        this.client = RestClient.builder().baseUrl("https://api.twilio.com")
                .defaultHeaders(headers -> headers.setBasicAuth(accountSid, authToken)).build();
        this.accountSid = accountSid;
        this.from = from;
    }

    @Override
    public void send(String phoneE164, String message) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phoneE164);
        form.add("From", from);
        form.add("Body", message);
        client.post().uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().toBodilessEntity();
    }
}
