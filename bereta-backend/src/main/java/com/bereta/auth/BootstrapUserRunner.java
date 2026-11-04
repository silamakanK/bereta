package com.bereta.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class BootstrapUserRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(BootstrapUserRunner.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String displayName;

    public BootstrapUserRunner(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${bereta.auth.bootstrap.email:}") String email,
            @Value("${bereta.auth.bootstrap.password:}") String password,
            @Value("${bereta.auth.bootstrap.display-name:Responsable BERETA}") String displayName
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Configurez ensemble BOOTSTRAP_USER_EMAIL et BOOTSTRAP_USER_PASSWORD.");
        }
        if (password.length() < 6) {
            throw new IllegalStateException("Le mot de passe initial doit contenir au moins 6 caractères.");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Le mot de passe initial ne peut pas dépasser 72 octets UTF-8.");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserAccountEntity account = userAccountRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (account == null) {
            account = userAccountRepository.save(new UserAccountEntity(
                    normalizedEmail,
                    displayName.isBlank() ? "Responsable BERETA" : displayName.trim(),
                    passwordEncoder.encode(password),
                    "RESPONSABLE"
            ));
            logger.info("Le compte responsable initial a été créé à partir de la configuration locale.");
        }
        if (!"RESPONSABLE".equals(account.getRole())) {
            throw new IllegalStateException("BOOTSTRAP_USER_EMAIL doit correspondre à un compte responsable.");
        }
    }
}
