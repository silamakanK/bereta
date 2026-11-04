package com.bereta.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserRegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisteredAccountResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le mot de passe ne peut pas dépasser 72 octets UTF-8.");
        }
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
        }
        String displayName = request.firstName().trim() + " " + request.lastName().trim();
        if (displayName.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La longueur combinée du prénom et du nom ne peut pas dépasser 120 caractères.");
        }
        try {
            userAccountRepository.saveAndFlush(new UserAccountEntity(
                    email, displayName, passwordEncoder.encode(request.password()), "TRESORIER"));
        } catch (DataIntegrityViolationException duplicateEmail) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette adresse e-mail est déjà utilisée.");
        }
        return new RegisteredAccountResponse(email, displayName);
    }
}
