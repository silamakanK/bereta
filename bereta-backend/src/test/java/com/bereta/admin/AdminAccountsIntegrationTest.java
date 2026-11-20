package com.bereta.admin;

import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Espace administrateur, lot 1 : accès réservé au rôle ADMIN, comptes (liste masquée, fiche, désactivation,
 * réactivation, mot de passe provisoire), garde-fous et journal d'audit (une entrée par action réussie, aucune sinon).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AdminAccountsIntegrationTest {

    private static final String PASSWORD = "Bereta2025";
    private static final String REASON = "{\"reason\":\"Demande écrite du responsable, ticket 42\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void everyAdminRouteIsForbiddenToManagersAndTreasurers() throws Exception {
        String manager = token(account("RESPONSABLE"));
        String treasurer = token(account("TRESORIER"));
        UUID someone = account("RESPONSABLE").getId();
        UUID association = UUID.randomUUID();
        List<MockHttpServletRequestBuilder> routes = List.of(
                get("/api/admin/overview"), get("/api/admin/users"), get("/api/admin/users/" + someone),
                post("/api/admin/users/" + someone + "/disable"), post("/api/admin/users/" + someone + "/enable"),
                post("/api/admin/users/" + someone + "/reset-password"), get("/api/admin/audit-logs"),
                get("/api/admin/audit-logs/export"), get("/api/admin/associations"),
                get("/api/admin/associations/" + association), post("/api/admin/associations/" + association + "/archive"));
        for (String token : List.of(manager, treasurer)) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(route.header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON).content(REASON))
                        .andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void disablingAnAccountClosesItsSessionsAndIsAuditedExactlyOnce() throws Exception {
        String admin = token(account("ADMIN"));
        UserAccountEntity user = account("RESPONSABLE");
        MvcResult session = login(user);
        String userToken = accessToken(session);
        Cookie refresh = refreshCookie(session);

        // Motif trop court : refusé, rien n'est journalisé ni modifié.
        call(admin, post("/api/admin/users/" + user.getId() + "/disable"), "{\"reason\":\"court\"}")
                .andExpect(status().isBadRequest());
        assertThat(auditCount(user.getId())).isZero();

        call(admin, post("/api/admin/users/" + user.getId() + "/disable"), REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedSessions").value(1))
                .andExpect(jsonPath("$.user.enabled").value(false));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(refresh).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(loginBody(user)))
                .andExpect(status().is4xxClientError());

        assertThat(auditCount(user.getId())).isEqualTo(1);
        var entry = jdbc.queryForMap("select action, reason, ip_address, actor_user_id from admin_audit_log where target_id = ?",
                user.getId());
        assertThat(entry).containsEntry("ACTION", "USER_DISABLED")
                .containsEntry("REASON", "Demande écrite du responsable, ticket 42");
        assertThat(entry.get("IP_ADDRESS")).isNotNull();

        // Déjà désactivé : refusé, aucune nouvelle entrée.
        call(admin, post("/api/admin/users/" + user.getId() + "/disable"), REASON).andExpect(status().isConflict());
        assertThat(auditCount(user.getId())).isEqualTo(1);

        call(admin, post("/api/admin/users/" + user.getId() + "/enable"), REASON)
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.enabled").value(true));
        call(admin, post("/api/admin/users/" + user.getId() + "/enable"), REASON).andExpect(status().isConflict());
        login(user);
        assertThat(auditCount(user.getId())).isEqualTo(2);

        call(admin, get("/api/admin/audit-logs?targetId=" + user.getId() + "&action=USER_DISABLED"), null)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].before.enabled").value(true))
                .andExpect(jsonPath("$.items[0].after.enabled").value(false))
                .andExpect(jsonPath("$.items[0].after.closedSessions").value(1));
    }

    @Test
    void anAdministratorCannotDisableHimselfAndAnotherAdminCanBeDisabled() throws Exception {
        UserAccountEntity first = account("ADMIN");
        String admin = token(first);
        call(admin, post("/api/admin/users/" + first.getId() + "/disable"), REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Vous ne pouvez pas désactiver votre propre compte."));
        call(admin, post("/api/admin/users/" + first.getId() + "/reset-password"), REASON).andExpect(status().isConflict());
        assertThat(auditCount(first.getId())).isZero();

        UserAccountEntity second = account("ADMIN");
        call(admin, post("/api/admin/users/" + second.getId() + "/disable"), REASON).andExpect(status().isOk());
        call(admin, post("/api/admin/users/" + UUID.randomUUID() + "/disable"), REASON).andExpect(status().isNotFound());
    }

    @Test
    void temporaryPasswordIsShownOnceAndNeverLogged(CapturedOutput output) throws Exception {
        String admin = token(account("ADMIN"));
        UserAccountEntity user = account("TRESORIER");
        login(user);

        JsonNode reset = objectMapper.readTree(call(admin, post("/api/admin/users/" + user.getId() + "/reset-password"), REASON)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.closedSessions").value(1))
                .andReturn().getResponse().getContentAsString());
        String temporary = reset.get("temporaryPassword").asText();
        assertThat(temporary).hasSize(10);

        assertThat(output.getAll()).doesNotContain(temporary);
        String audit = jdbc.queryForList("select coalesce(reason,'') || coalesce(details,'') || coalesce(cast(before_values as varchar),'') "
                + "|| coalesce(cast(after_values as varchar),'') from admin_audit_log where target_id = ?", String.class, user.getId()).toString();
        assertThat(audit).contains("mustChangePassword").doesNotContain(temporary);
        assertThat(jdbc.queryForList("select coalesce(details,'') from auth_audit_log where user_account_id = ?",
                String.class, user.getId()).toString()).doesNotContain(temporary);
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(user.getPhoneE164(), temporary)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.mustChangePassword").value(true));
    }

    @Test
    void listMasksContactsWhileTheDetailShowsThemWithAccessesAndSessions() throws Exception {
        String admin = token(account("ADMIN"));
        UserAccountEntity user = account("TRESORIER");
        login(user);
        String national = "0" + user.getPhoneE164().substring(3);

        JsonNode page = json(call(admin, get("/api/admin/users?q=" + user.getEmail() + "&role=TRESORIER&enabled=true&sort=name&direction=asc&size=5"), null)
                .andExpect(status().isOk()));
        assertThat(page.get("totalItems").asLong()).isEqualTo(1);
        JsonNode row = page.at("/items/0");
        assertThat(row.get("phone").asText()).matches("0\\d •• •• •• \\d\\d").endsWith(national.substring(8));
        assertThat(row.get("email").asText()).startsWith(user.getEmail().substring(0, 2)).contains("•••@");

        call(admin, get("/api/admin/users/" + user.getId()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.phone").value(national.replaceAll("(\\d\\d)(?=\\d)", "$1 ")))
                .andExpect(jsonPath("$.activeSessions").value(1))
                .andExpect(jsonPath("$.lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.associations").isArray());

        // Recherche par téléphone, filtres de rôle et de statut, pagination.
        call(admin, get("/api/admin/users?q=" + national.substring(1) + "&size=1"), null)
                .andExpect(jsonPath("$.items[0].id").value(user.getId().toString()));
        call(admin, get("/api/admin/users?role=ADMIN&enabled=false&size=200"), null)
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void archivingAnAssociationRequiresAReasonAndIsAudited() throws Exception {
        UserAccountEntity adminAccount = account("ADMIN");
        String admin = token(adminAccount);
        UserAccountEntity managerAccount = account("RESPONSABLE");
        String manager = token(managerAccount);
        String association = json(call(manager, post("/api/associations"), "{\"name\":\"Archive test\"}")
                .andExpect(status().isCreated())).get("id").asText();

        call(admin, get("/api/admin/overview"), null).andExpect(status().isOk()).andExpect(jsonPath("$.administrators").isNumber());
        call(admin, get("/api/admin/associations?q=Archive test"), null)
                .andExpect(jsonPath("$.items[0].name").value("Archive test"));
        call(admin, get("/api/admin/associations/" + association), null).andExpect(jsonPath("$.status").value("ACTIVE"));
        call(admin, get("/api/admin/associations/" + UUID.randomUUID()), null).andExpect(status().isNotFound());
        // Fiches : le responsable et son association ; un trésorier désigné et sa caisse locale.
        call(admin, get("/api/admin/users/" + managerAccount.getId()), null)
                .andExpect(jsonPath("$.associations[0].name").value("Archive test"));
        String primary = json(call(manager, get("/api/caisses-principales").header("X-Association-Id", association), null))
                .get(0).get("id").asText();
        String localFund = json(call(manager, post("/api/caisses-principales/" + primary + "/caisses-locales")
                .header("X-Association-Id", association), "{\"name\":\"Caisse Kayes\"}").andExpect(status().isCreated())).get("id").asText();
        String phone = "+3379" + "%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000));
        String treasurerId = json(call(manager, post("/api/caisses-principales/" + primary + "/acces").header("X-Association-Id", association),
                "{\"localFundId\":\"%s\",\"phone\":\"%s\",\"firstName\":\"Samba\",\"lastName\":\"Keita\"}".formatted(localFund, phone))
                .andExpect(status().isCreated())).at("/membership/userId").asText();
        call(admin, get("/api/admin/users/" + treasurerId), null)
                .andExpect(jsonPath("$.funds[0].role").value("TRESORIER"))
                .andExpect(jsonPath("$.funds[0].localFundName").value("Caisse Kayes"))
                .andExpect(jsonPath("$.funds[0].associationName").value("Archive test"));

        call(admin, post("/api/admin/associations/" + association + "/archive"), "{}").andExpect(status().isBadRequest());
        call(admin, post("/api/admin/associations/" + association + "/archive"), REASON)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        call(admin, post("/api/admin/associations/" + association + "/archive"), REASON).andExpect(status().isConflict());
        call(admin, get("/api/admin/audit-logs?associationId=" + association + "&action=ASSOCIATION_ARCHIVED"), null)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].associationName").value("Archive test"))
                .andExpect(jsonPath("$.items[0].actorId").value(adminAccount.getId().toString()));
        call(admin, get("/api/admin/audit-logs?associationId=" + association + "&from=2000-01-01&to=2000-01-02"), null)
                .andExpect(jsonPath("$.totalItems").value(0));

        String csv = call(admin, get("/api/admin/audit-logs/export?associationId=" + association), null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"journal-administration.csv\""))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Date;Administrateur;Action").contains("ASSOCIATION_ARCHIVED")
                .contains("Demande écrite du responsable, ticket 42").contains("status=ARCHIVED");
    }

    // ---- Outils ------------------------------------------------------------------------------

    private UserAccountEntity account(String role) {
        String phone = "+3377" + "%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000));
        return users.save(new UserAccountEntity("adm-" + UUID.randomUUID() + "@example.test", phone,
                "Compte " + role.toLowerCase(), passwordEncoder.encode(PASSWORD), role));
    }

    private String token(UserAccountEntity user) throws Exception {
        return accessToken(login(user));
    }

    private MvcResult login(UserAccountEntity user) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(loginBody(user)))
                .andExpect(status().isOk()).andReturn();
    }

    private static String loginBody(UserAccountEntity user) {
        return "{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(user.getPhoneE164(), PASSWORD);
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("bereta_refresh");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long auditCount(UUID targetId) {
        return jdbc.queryForObject("select count(*) from admin_audit_log where target_id = ?", Long.class, targetId);
    }
}
