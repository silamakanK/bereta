package com.bereta.auth;

public interface PasswordResetEmailSender {
    void sendResetLink(String email, String resetUrl);
}
