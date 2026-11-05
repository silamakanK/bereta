package com.bereta.auth;

import com.bereta.TestSecurity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentification simple : inscription et connexion ouvrent directement la session (aucun code). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingNotifications.class)
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "Bereta2025";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthAuditRepository auditLog;

    @BeforeEach
    void reset() {
        CapturingNotifications.reset();
    }

    // ---- Inscription -------------------------------------------------------------------------

    @Test
    void registrationCreatesAnActiveAccountAndOpensTheSession() throws Exception {
        String phone = uniquePhone();
        MvcResult registered = perform(postJson("/api/auth/register",
                "{\"phone\":\"%s\",\"password\":\"%s\",\"acceptTerms\":true}".formatted(national(phone), PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.user.phone").value(phone))
                .andExpect(jsonPath("$.user.role").value("RESPONSABLE"))
                .andReturn();
        refreshCookie(registered);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(registered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(phone));
        UserAccountEntity saved = users.findByPhoneE164(phone).orElseThrow();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void registrationValidatesPasswordTermsPhoneAndUniqueness() throws Exception {
        String phone = uniquePhone();
        String email = uniqueEmail();
        String template = "{\"phone\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"acceptTerms\":%s}";
        perform(postJson("/api/auth/register", template.formatted(phone, email, "sanschiffre", true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
        perform(postJson("/api/auth/register", template.formatted(phone, email, PASSWORD, false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
        perform(postJson("/api/auth/register", template.formatted("123", email, PASSWORD, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_INVALID"));
        perform(postJson("/api/auth/register", "{\"email\":\"%s\",\"password\":\"%s\",\"acceptTerms\":true}"
                .formatted(email, PASSWORD))).andExpect(status().isBadRequest());

        perform(postJson("/api/auth/register", template.formatted(phone, email, PASSWORD, true)))
                .andExpect(status().isCreated());
        perform(postJson("/api/auth/register", template.formatted(national(phone), uniqueEmail(), PASSWORD, true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"));
        perform(postJson("/api/auth/register", template.formatted(uniquePhone(), email.toUpperCase(), PASSWORD, true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"));
    }

    // ---- Connexion ---------------------------------------------------------------------------

    @Test
    void loginByEmailOrPhoneIssuesTokensDirectly() throws Exception {
        UserAccountEntity user = activeUser();
        MvcResult byEmail = perform(postJson("/api/auth/login", loginBody(user.getEmail().toUpperCase(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andReturn();
        refreshCookie(byEmail);
        MvcResult byPhone = perform(postJson("/api/auth/login", loginBody(national(user.getPhoneE164()), PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(byPhone)))
                .andExpect(status().isOk());
        assertThat(auditLog.findAllByUserAccountIdOrderByCreatedAtAsc(user.getId()))
                .extracting(AuthAuditEntity::getEvent).contains("LOGIN_SUCCEEDED");
    }

    @Test
    void unknownIdentifierWrongPasswordAndDisabledAccountGetTheSameGenericAnswer() throws Exception {
        UserAccountEntity user = activeUser();
        String wrongPassword = perform(postJson("/api/auth/login", loginBody(user.getEmail(), "Mauvais123")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = perform(postJson("/api/auth/login", loginBody("inconnu@example.test", "Mauvais123")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(unknown).get("detail"))
                .isEqualTo(objectMapper.readTree(wrongPassword).get("detail"));
        assertThat(objectMapper.readTree(unknown).get("detail").asText()).startsWith("Identifiants incorrects");

        UserAccountEntity disabled = users.findById(user.getId()).orElseThrow();
        disabled.setEnabled(false);
        users.save(disabled);
        perform(postJson("/api/auth/login", loginBody(user.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountForFifteenMinutes() throws Exception {
        UserAccountEntity user = activeUser();
        for (int i = 0; i < 5; i++) {
            perform(postJson("/api/auth/login", loginBody(user.getEmail(), "Mauvais123")))
                    .andExpect(status().isUnauthorized());
        }
        perform(postJson("/api/auth/login", loginBody(user.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        assertThat(auditLog.findAllByUserAccountIdOrderByCreatedAtAsc(user.getId()))
                .extracting(AuthAuditEntity::getEvent).contains("ACCOUNT_LOCKED");

        CapturingNotifications.CLOCK.advance(Duration.ofMinutes(16));
        perform(postJson("/api/auth/login", loginBody(user.getEmail(), PASSWORD))).andExpect(status().isOk());
    }

    // ---- Refresh token, déconnexion ----------------------------------------------------------

    @Test
    void refreshTokenRotatesAndReuseRevokesTheWholeFamily() throws Exception {
        UserAccountEntity user = activeUser();
        Cookie first = refreshCookie(login(user));

        mockMvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isForbidden());
        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh").cookie(first).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString()).andReturn();
        Cookie second = refreshCookie(rotated);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mockMvc.perform(post("/api/auth/refresh").cookie(first).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(second).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isUnauthorized());
        assertThat(auditLog.findAllByUserAccountIdOrderByCreatedAtAsc(user.getId()))
                .extracting(AuthAuditEntity::getEvent).contains("REFRESH_REUSE_DETECTED");
    }

    @Test
    void logoutRevokesRefreshTokenAndLogoutAllInvalidatesAccessTokens() throws Exception {
        UserAccountEntity user = activeUser();
        MvcResult session = login(user);
        mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie(session)).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(session)).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isUnauthorized());

        MvcResult other = login(user);
        String access = accessToken(other);
        mockMvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(other)).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isUnauthorized());
    }

    // ---- Mot de passe ------------------------------------------------------------------------

    @Test
    void forgotPasswordIsDisabledForNow() throws Exception {
        UserAccountEntity user = activeUser();
        perform(postJson("/api/auth/password/forgot", "{\"identifier\":\"%s\"}".formatted(user.getEmail())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FORGOT_PASSWORD_DISABLED"));
        assertThat(CapturingNotifications.EMAILS).isEmpty();
    }

    @Test
    void administratorResetsAPasswordWithATemporaryOneThatMustBeChanged() throws Exception {
        UserAccountEntity user = activeUser();
        MvcResult oldSession = login(user);
        UserAccountEntity admin = TestSecurity.account(users, passwordEncoder, "admin-" + UUID.randomUUID() + "@example.test", "ADMIN");
        String adminToken = accessToken(perform(postJson("/api/auth/login", loginBody(admin.getEmail(), "TestPassword!234")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(post("/api/admin/users/" + user.getId() + "/password").header("Authorization", "Bearer " + accessToken(oldSession)))
                .andExpect(status().isForbidden());
        JsonNode reset = objectMapper.readTree(mockMvc.perform(post("/api/admin/users/" + user.getId() + "/password")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String temporary = reset.get("temporaryPassword").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(oldSession)))
                .andExpect(status().isUnauthorized());
        perform(postJson("/api/auth/login", loginBody(user.getEmail(), PASSWORD))).andExpect(status().isUnauthorized());
        MvcResult temporarySession = perform(postJson("/api/auth/login", loginBody(user.getEmail(), temporary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true)).andReturn();
        String access = accessToken(temporarySession);
        mockMvc.perform(get("/api/associations").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        MvcResult changed = mockMvc.perform(post("/api/auth/password/change").header("Authorization", "Bearer " + access)
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"Personnel2025\"}".formatted(temporary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false)).andReturn();
        mockMvc.perform(get("/api/associations").header("Authorization", "Bearer " + accessToken(changed)))
                .andExpect(status().isOk());
        assertThat(auditLog.findAllByUserAccountIdOrderByCreatedAtAsc(user.getId()))
                .extracting(AuthAuditEntity::getEvent).contains("TEMPORARY_PASSWORD_ISSUED", "PASSWORD_CHANGED");
    }

    // ---- Profil ------------------------------------------------------------------------------

    @Test
    void profileCanBeCompletedAfterRegistration() throws Exception {
        String phone = uniquePhone();
        String access = accessToken(perform(postJson("/api/auth/register",
                "{\"phone\":\"%s\",\"password\":\"%s\",\"acceptTerms\":true}".formatted(phone, PASSWORD)))
                .andExpect(status().isCreated()).andReturn());
        String email = uniqueEmail();
        mockMvc.perform(patch("/api/auth/profile").header("Authorization", "Bearer " + access)
                        .contentType(APPLICATION_JSON)
                        .content("{\"firstName\":\"Awa\",\"lastName\":\"Diallo\",\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Awa Diallo"))
                .andExpect(jsonPath("$.email").value(email));
        perform(postJson("/api/auth/login", loginBody(email, PASSWORD))).andExpect(status().isOk());
        assertThat(CapturingNotifications.EMAILS).isEmpty();
    }

    // ---- Outils ------------------------------------------------------------------------------

    private UserAccountEntity activeUser() {
        return users.save(new UserAccountEntity(uniqueEmail(), uniquePhone(), "Compte actif",
                passwordEncoder.encode(PASSWORD), "RESPONSABLE"));
    }

    private MvcResult login(UserAccountEntity user) throws Exception {
        return perform(postJson("/api/auth/login", loginBody(national(user.getPhoneE164()), PASSWORD)))
                .andExpect(status().isOk()).andReturn();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    private static MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).contentType(APPLICATION_JSON).content(body);
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("bereta_refresh");
        assertThat(cookie).as("cookie de rafraîchissement").isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private static String loginBody(String identifier, String password) {
        return "{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password);
    }

    /** Mobile français valide (076…) au format E.164, distinct des numéros de TestSecurity (06). */
    private static String uniquePhone() {
        return "+3376" + "%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000));
    }

    /** +33712345678 → 07 12 34 56 78 (saisie naturelle d'un utilisateur). */
    private static String national(String e164) {
        String digits = "0" + e164.substring(3);
        return String.join(" ", digits.split("(?<=\\G..)"));
    }

    private static String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@example.test";
    }
}
