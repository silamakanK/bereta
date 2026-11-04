package com.bereta.notifications;

public interface EmailSender {
    void send(String to, String subject, String body);
}
