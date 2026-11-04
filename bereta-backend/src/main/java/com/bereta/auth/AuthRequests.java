package com.bereta.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Corps des requêtes d'authentification. Les règles métier (mot de passe, téléphone) sont dans les services. */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Register(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 30) String phone,
            @Email @Size(max = 320) String email,
            @NotNull @Size(max = 128) String password,
            boolean acceptTerms) {
    }

    public record Login(@NotBlank @Size(max = 320) String identifier, @NotNull @Size(max = 128) String password) {
    }

    public record PhoneEnrollment(@NotBlank @Size(max = 2048) String enrollmentToken,
                                  @NotBlank @Size(max = 30) String phone) {
    }

    public record VerifyCode(@NotNull UUID challengeId, @NotBlank @Size(max = 10) String code) {
    }

    public record ResendCode(@NotNull UUID challengeId) {
    }

    public record ForgotPassword(@NotBlank @Size(max = 320) String identifier) {
    }

    public record ResetPasswordWithLink(@NotBlank @Size(max = 128) String token,
                                        @NotNull @Size(max = 128) String password) {
    }

    public record ResetPasswordWithCode(@NotNull UUID challengeId, @NotBlank @Size(max = 10) String code,
                                        @NotNull @Size(max = 128) String password) {
    }

    public record ChangePassword(@NotNull @Size(max = 128) String currentPassword,
                                 @NotNull @Size(max = 128) String newPassword) {
    }

    public record EmailCode(@NotBlank @Size(max = 10) String code) {
    }
}
