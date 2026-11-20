package com.bereta.admin;

import com.bereta.auth.AuthException;
import com.bereta.auth.BeretaUserDetails;
import com.bereta.auth.PasswordService;
import com.bereta.auth.RateLimiterService;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.auth.token.RefreshTokenService;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Règles unitaires de l'espace administrateur, y compris celles que l'API ne permet pas d'atteindre. */
class AdminUnitsTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void masksPhonesAndEmailsInLists() {
        assertThat(AdminMasking.phone("06 12 34 56 78")).isEqualTo("06 •• •• •• 78");
        assertThat(AdminMasking.phone("+223 76 12 34 56")).isEqualTo("+223 76 •• •• 56");
        assertThat(AdminMasking.phone(null)).isNull();
        assertThat(AdminMasking.email("silamakan@gmail.com")).isEqualTo("si•••@gmail.com");
        assertThat(AdminMasking.email("a@b.fr")).isEqualTo("a•••@b.fr");
        assertThat(AdminMasking.email("sans-arobase")).isEqualTo("•••");
        assertThat(AdminMasking.email(" ")).isNull();
        var phones = new com.bereta.auth.PhoneNumberService("FR");
        assertThat(phones.display("+33612345678")).isEqualTo("06 12 34 56 78");
        assertThat(phones.display("+22376123456")).isEqualTo("+223 76 12 34 56");
        assertThat(phones.display("pas un numéro")).isEqualTo("pas un numéro");
        assertThat(phones.display(null)).isNull();
    }

    @Test
    void csvCellsAreQuotedAndFormulasNeutralised() {
        assertThat(AdminAuditReader.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(AdminAuditReader.cell("+33 6")).isEqualTo("\"'+33 6\"");
        assertThat(AdminAuditReader.cell("Motif normal")).isEqualTo("\"Motif normal\"");
        assertThat(AdminAuditReader.cell("")).isEqualTo("\"\"");
        assertThat(AdminAuditReader.cell(null)).isEmpty();
    }

    @Test
    void auditEntriesNeverKeepSecrets() {
        var entry = new AdminAuditService.Entry(AdminAction.USER_PASSWORD_RESET, "USER", UUID.randomUUID(), null,
                "Motif suffisamment long", Map.of("temporaryPassword", "abc", "mustChangePassword", true),
                Map.of("refresh_token", "x", "password_hash", "y", "role", "TRESORIER")).withoutSecrets();
        assertThat(entry.before()).containsOnlyKeys("mustChangePassword");
        assertThat(entry.after()).containsOnlyKeys("role");
        assertThat(new AdminAuditService.Entry(AdminAction.USER_ENABLED, "USER", null, null, "x", null, null)
                .withoutSecrets().before()).isNull();
    }

    @Test
    void theAuditLogIsAppendOnly() {
        assertThat(AdminAuditEntity.class.isAnnotationPresent(Immutable.class)).isTrue();
        List<String> methods = Arrays.stream(AdminAuditRepository.class.getMethods()).map(Method::getName).toList();
        assertThat(methods).containsOnly("save", "findById", "count");
        assertThat(Arrays.stream(AdminAuditEntity.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void theLastActiveAdministratorCanNeverBeDisabled() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        AdminUserReader reader = mock(AdminUserReader.class);
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserAccountEntity actor = mock(UserAccountEntity.class);
        UserAccountEntity target = mock(UserAccountEntity.class);
        when(target.getId()).thenReturn(targetId);
        when(target.isEnabled()).thenReturn(true);
        when(target.getRole()).thenReturn("ADMIN");
        when(reader.require(actorId)).thenReturn(actor);
        when(reader.require(targetId)).thenReturn(target);
        // L'auteur vient d'être désactivé par ailleurs : la cible est le seul administrateur actif restant.
        when(users.lockActiveAdministrators()).thenReturn(List.of(target));
        AdminUserService service = new AdminUserService(users, reader, mock(RefreshTokenService.class),
                mock(PasswordService.class), mock(AdminAuditService.class));

        assertThatThrownBy(() -> service.disable(actorId, targetId, "Motif suffisamment long"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dernier administrateur actif");
    }

    @Test
    void adminRoutesAreRateLimitedPerAdministrator() {
        var interceptor = new AdminRateLimitConfiguration.AdminRateLimitInterceptor(new RateLimiterService(true));
        UserAccountEntity admin = mock(UserAccountEntity.class);
        when(admin.getId()).thenReturn(UUID.randomUUID());
        when(admin.getRole()).thenReturn("ADMIN");
        when(admin.isEnabled()).thenReturn(true);
        BeretaUserDetails principal = new BeretaUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        var request = new MockHttpServletRequest("GET", "/api/admin/users");
        var response = new MockHttpServletResponse();
        for (int i = 0; i < 120; i++) assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(AuthException.class);

        // Sans session : la limite s'applique à l'adresse IP.
        SecurityContextHolder.clearContext();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())).isEmpty();
    }
}
