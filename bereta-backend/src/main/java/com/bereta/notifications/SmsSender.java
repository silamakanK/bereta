package com.bereta.notifications;

public interface SmsSender {
    /** @param phoneE164 numéro au format E.164, par exemple +33612345678 */
    void send(String phoneE164, String message);
}
