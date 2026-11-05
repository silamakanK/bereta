package com.bereta.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps des requêtes d'authentification. Les règles métier (mot de passe, téléphone) sont dans les services. */
public final class AuthRequests {

    private AuthRequests() {
    }

    /** Téléphone + mot de passe + CGU ; prénom, nom et e-mail facultatifs. */
    public record Register(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @NotBlank @Size(max = 30) String phone,
            @Email @Size(max = 320) String email,
            @NotNull @Size(max = 128) String password,
            boolean acceptTerms) {
    }

    /** Champs facultatifs : un champ absent (null) n'est pas modifié, une chaîne vide efface l'e-mail. */
    public record UpdateProfile(@Size(max = 100) String firstName, @Size(max = 100) String lastName,
                                @Size(max = 320) String email) {
    }

    public record Login(@NotBlank @Size(max = 320) String identifier, @NotNull @Size(max = 128) String password) {
    }

    public record ForgotPassword(@NotBlank @Size(max = 320) String identifier) {
    }

    public record ResetPasswordWithLink(@NotBlank @Size(max = 128) String token,
                                        @NotNull @Size(max = 128) String password) {
    }

    public record ChangePassword(@NotNull @Size(max = 128) String currentPassword,
                                 @NotNull @Size(max = 128) String newPassword) {
    }
}
