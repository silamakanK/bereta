package com.bereta.admin;

import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Le journal s'écrit dans la transaction de l'action : s'il échoue, l'action est annulée. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditAtomicityTest {

    @Autowired private AdminUserService service;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AdminAuditService audit;
    @MockitoBean private AdminAuditRepository repository;

    @Test
    void aFailingAuditWriteCancelsTheAction() {
        UserAccountEntity admin = account("ADMIN");
        UserAccountEntity user = account("RESPONSABLE");
        doThrow(new IllegalStateException("journal indisponible")).when(repository).save(any());

        assertThatThrownBy(() -> service.disable(admin.getId(), user.getId(), "Motif suffisamment long"))
                .hasMessageContaining("journal indisponible");
        assertThat(users.findById(user.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void theAuditCannotBeWrittenOutsideTheActionTransaction() {
        assertThatThrownBy(() -> audit.record(null, new AdminAuditService.Entry(AdminAction.USER_ENABLED, "USER",
                UUID.randomUUID(), null, "Motif suffisamment long", null, null)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private UserAccountEntity account(String role) {
        String phone = "+3378" + "%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000));
        return users.save(new UserAccountEntity("atom-" + UUID.randomUUID() + "@example.test", phone, "Compte",
                passwordEncoder.encode("Bereta2025"), role));
    }
}
