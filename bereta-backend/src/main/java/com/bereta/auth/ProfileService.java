package com.bereta.auth;

import com.bereta.auth.AuthRequests.UpdateProfile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/** « Complète ton profil » : prénom, nom et e-mail, tous facultatifs (aucune vérification). */
@Service
public class ProfileService {

    private final UserAccountRepository users;
    private final AuthAuditService audit;

    public ProfileService(UserAccountRepository users, AuthAuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = AuthException.class)
    public UserAccountEntity update(UUID userId, UpdateProfile request) {
        UserAccountEntity user = users.findByIdForUpdate(userId).orElseThrow(AuthException::invalidCredentials);

        String displayName = user.getDisplayName();
        if (request.firstName() != null || request.lastName() != null) {
            displayName = displayName(request.firstName(), request.lastName());
        }

        String email = user.getEmail();
        boolean emailChanged = false;
        if (request.email() != null) {
            String requested = request.email().isBlank() ? null : request.email().trim().toLowerCase(Locale.ROOT);
            if (requested != null && !requested.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw AuthException.badRequest("EMAIL_INVALID", "L’adresse e-mail n’est pas valide.");
            }
            if (requested != null && !requested.equalsIgnoreCase(user.getEmail())
                    && users.existsByEmailIgnoreCase(requested)) {
                // Message volontairement neutre : ne pas confirmer qu'un autre compte utilise cette adresse.
                throw new AuthException(HttpStatus.CONFLICT, "EMAIL_UNAVAILABLE",
                        "Cette adresse e-mail ne peut pas être utilisée. Essayez-en une autre.");
            }
            emailChanged = requested == null ? user.getEmail() != null : !requested.equalsIgnoreCase(user.getEmail());
            email = requested;
        }
        if (email == null && user.getPhoneE164() == null) {
            throw AuthException.badRequest("IDENTIFIER_REQUIRED",
                    "Gardez une adresse e-mail : c’est le seul moyen de vous connecter à ce compte.");
        }

        user.updateProfile(email, displayName);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_UNAVAILABLE",
                    "Cette adresse e-mail ne peut pas être utilisée. Essayez-en une autre.");
        }
        audit.record(AuthEvent.PROFILE_COMPLETED, user.getId(), null, emailChanged ? "E-mail modifié" : null);
        return user;
    }

    /** Prénom + nom, ou null si les deux sont vides. */
    static String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String joined = (first + " " + last).trim();
        if (joined.length() > 120) {
            throw AuthException.badRequest("NAME_TOO_LONG",
                    "Le prénom et le nom ne peuvent pas dépasser 120 caractères au total.");
        }
        return joined.isEmpty() ? null : joined;
    }
}
