package com.bereta.auth;

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

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void registrationKeepsAccountPendingUntilPhoneCodeThenLogsIn() throws Exception {
        String phone = uniquePhone();
        JsonNode registration = json(postJson("/api/auth/register", """
                {"firstName":"Fatoumata","lastName":"Traoré","phone":"%s","email":"%s",
                 "password":"%s","acceptTerms":true}
                """.formatted(national(phone), uniqueEmail(), PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phoneVerification.maskedDestination").value(containsString("** ** **")))
                .andExpect(jsonPath("$.emailVerificationSent").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist()));

        UserAccountEntity pending = users.findByPhoneE164(phone).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(pending.getRole()).isEqualTo("RESPONSABLE");
        assertThat(pending.getPasswordHash()).startsWith("$2");
        String challengeId = registration.at("/phoneVerification/challengeId").asText();

        perform(jsonPost("/api/auth/register/verify", verifyBody(challengeId, "000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Il vous reste 4 essais")));

        MvcResult verified = perform(jsonPost("/api/auth/register/verify",
                verifyBody(challengeId, CapturingNotifications.lastCodeSentTo(phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.phoneVerified").value(true))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andReturn();
        assertThat(users.findByPhoneE164(phone).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(verified)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void registrationValidatesPasswordTermsPhoneAndUniqueness() throws Exception {
        String phone = uniquePhone();
        String template = """
                {"firstName":"Awa","lastName":"Diallo","phone":"%s","password":"%s","acceptTerms":%s}
                """;
        perform(jsonPost("/api/auth/register", template.formatted(phone, "sanschiffre", true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
        perform(jsonPost("/api/auth/register", template.formatted(phone, PASSWORD, false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
        perform(jsonPost("/api/auth/register", template.formatted("123", PASSWORD, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_INVALID"));
        perform(jsonPost("/api/auth/register", template.formatted(phone, PASSWORD, true)))
                .andExpect(status().isCreated());
        perform(jsonPost("/api/auth/register", template.formatted(national(phone), PASSWORD, true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
    }

    @Test
    void emailIsVerifiedWithTheCodeSentAtRegistration() throws Exception {
        String phone = uniquePhone();
        String email = uniqueEmail();
        JsonNode registration = json(postJson("/api/auth/register", """
                {"firstName":"Moussa","lastName":"Fofana","phone":"%s","email":"%s","password":"%s","acceptTerms":true}
                """.formatted(phone, email, PASSWORD)).andExpect(status().isCreated()));
        String token = accessToken(perform(jsonPost("/api/auth/register/verify",
                verifyBody(registration.at("/phoneVerification/challengeId").asText(),
                        CapturingNotifications.lastCodeSentTo(phone)))).andReturn());

        mockMvc.perform(post("/api/auth/email/verify").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(CapturingNotifications.lastCodeSentTo(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    // ---- Connexion en deux étapes ------------------------------------------------------------

    @Test
    void loginNeverIssuesTokenBeforeSmsCodeAndAcceptsEmailOrPhone() throws Exception {
        UserAccountEntity user = activeUser();
        perform(jsonPost("/api/auth/login", loginBody(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("OTP_REQUIRED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));

        JsonNode step = json(postJson("/api/auth/login", loginBody(national(user.getPhoneE164()), PASSWORD))
                .andExpect(status().isOk()));
        assertThat(step.get("maskedPhone").asText()).matches("\\+33 7 \\*\\* \\*\\* \\*\\* \\d{2}");

        MvcResult tokens = perform(jsonPost("/api/auth/login/verify", verifyBody(step.get("challengeId").asText(),
                CapturingNotifications.lastCodeSentTo(user.getPhoneE164()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andReturn();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(tokens)))
                .andExpect(status().isOk());
        assertThat(auditLog.findAllByUserAccountIdOrderByCreatedAtAsc(user.getId()))
                .extracting(AuthAuditEntity::getEvent).contains("LOGIN_SUCCEEDED");
    }

    @Test
    void unknownIdentifierAndWrongPasswordGetTheSameGenericAnswer() throws Exception {
        UserAccountEntity user = activeUser();
        String wrongPassword = perform(jsonPost("/api/auth/login", loginBody(user.getEmail(), "Mauvais123")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = perform(jsonPost("/api/auth/login", loginBody("inconnu@example.test", "Mauvais123")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(unknown).get("detail"))
                .isEqualTo(objectMapper.readTree(wrongPassword).get("detail"));
        assertThat(objectMapper.readTree(unknown).get("detail").asText()).startsWith("Identifiants incorrects");
    }

    @Test
    void fiveWrongPasswordsLockTheAccountForFifteenMinutes() throws Exception {
        UserAccountEntity user = activeUser();
        for (int i = 0; i < 5; i++) {
            perform(jsonPost("/api/auth/login", loginBody(user.getEmail(), "Mauvais123")))
                    .andExpect(status().isUnauthorized());
        }
        perform(jsonPost("/api/auth/login", loginBody(user.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        assertThat(auditLog.findAllByUserAccountIdOrderByCreatedAtAsc(user.getId()))
                .extracting(AuthAuditEntity::getEvent).contains("ACCOUNT_LOCKED");

        CapturingNotifications.CLOCK.advance(Duration.ofMinutes(16));
        perform(jsonPost("/api/auth/login", loginBody(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void codeIsBlockedAfterFiveWrongAttemptsAndExpiresAfterFiveMinutes() throws Exception {
        UserAccountEntity user = activeUser();
        String challenge = startLogin(user);
        String code = CapturingNotifications.lastCodeSentTo(user.getPhoneE164());
        for (int i = 0; i < 5; i++) {
            perform(jsonPost("/api/auth/login/verify", verifyBody(challenge, "111111".equals(code) ? "222222" : "111111")))
                    .andExpect(status().isBadRequest());
        }
        perform(jsonPost("/api/auth/login/verify", verifyBody(challenge, code))).andExpect(status().isBadRequest());

        String second = startLogin(user);
        String secondCode = CapturingNotifications.lastCodeSentTo(user.getPhoneE164());
        CapturingNotifications.CLOCK.advance(Duration.ofMinutes(6));
        perform(jsonPost("/api/auth/login/verify", verifyBody(second, secondCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("expiré")));
    }

    @Test
    void resendRespectsCooldownThenReplacesTheCode() throws Exception {
        UserAccountEntity user = activeUser();
        String challenge = startLogin(user);
        String firstCode = CapturingNotifications.lastCodeSentTo(user.getPhoneE164());
        perform(jsonPost("/api/auth/otp/resend", "{\"challengeId\":\"%s\"}".formatted(challenge)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        CapturingNotifications.CLOCK.advance(Duration.ofSeconds(61));
        perform(jsonPost("/api/auth/otp/resend", "{\"challengeId\":\"%s\"}".formatted(challenge)))
                .andExpect(status().isOk());
        String secondCode = CapturingNotifications.lastCodeSentTo(user.getPhoneE164());
        if (!secondCode.equals(firstCode)) {
            perform(jsonPost("/api/auth/login/verify", verifyBody(challenge, firstCode)))
                    .andExpect(status().isBadRequest());
        }
        perform(jsonPost("/api/auth/login/verify", verifyBody(challenge, secondCode))).andExpect(status().isOk());
    }

    @Test
    void accountWithoutPhoneAddsAndVerifiesItDuringLogin() throws Exception {
        String email = uniqueEmail();
        users.save(new UserAccountEntity(email, "Ancien compte", passwordEncoder.encode(PASSWORD), "RESPONSABLE"));
        JsonNode step = json(postJson("/api/auth/login", loginBody(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("PHONE_REQUIRED")));
        String enrollmentToken = step.get("enrollmentToken").asText();

        // Le jeton d'enrôlement n'ouvre pas l'API.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + enrollmentToken))
                .andExpect(status().isUnauthorized());

        String phone = uniquePhone();
        JsonNode otp = json(postJson("/api/auth/login/phone",
                "{\"enrollmentToken\":\"%s\",\"phone\":\"%s\"}".formatted(enrollmentToken, national(phone)))
                .andExpect(status().isOk()));
        perform(jsonPost("/api/auth/login/verify", verifyBody(otp.get("challengeId").asText(),
                CapturingNotifications.lastCodeSentTo(phone)))).andExpect(status().isOk());
        UserAccountEntity updated = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(updated.getPhoneE164()).isEqualTo(phone);
        assertThat(updated.isPhoneVerified()).isTrue();
    }

    // ---- Refresh token, déconnexion ----------------------------------------------------------

    @Test
    void refreshTokenRotatesAndReuseRevokesTheWholeFamily() throws Exception {
        UserAccountEntity user = activeUser();
        Cookie first = refreshCookie(completeLogin(user));

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
        MvcResult session = completeLogin(user);
        mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie(session)).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(session)).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isUnauthorized());

        MvcResult other = completeLogin(user);
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
    void forgotPasswordAnswersIdenticallyAndSmsCodeResetDisconnectsEverything() throws Exception {
        UserAccountEntity user = activeUser();
        MvcResult oldSession = completeLogin(user);

        JsonNode unknown = json(postJson("/api/auth/password/forgot", "{\"identifier\":\"%s\"}".formatted(uniquePhone()))
                .andExpect(status().isOk()));
        JsonNode known = json(postJson("/api/auth/password/forgot",
                "{\"identifier\":\"%s\"}".formatted(national(user.getPhoneE164()))).andExpect(status().isOk()));
        assertThat(known.get("message")).isEqualTo(unknown.get("message"));
        assertThat(known.fieldNames()).toIterable().containsExactlyElementsOf(() -> unknown.fieldNames());
        assertThat(unknown.get("challengeId").isTextual()).isTrue();

        String challenge = known.get("challengeId").asText();
        String code = CapturingNotifications.lastCodeSentTo(user.getPhoneE164());
        perform(jsonPost("/api/auth/password/reset/code", """
                {"challengeId":"%s","code":"%s","password":"court1"}""".formatted(challenge, code)))
                .andExpect(status().isBadRequest());
        perform(jsonPost("/api/auth/password/reset/code", """
                {"challengeId":"%s","code":"%s","password":"Nouveau2025"}""".formatted(challenge, code)))
                .andExpect(status().isOk());
        perform(jsonPost("/api/auth/password/reset/code", """
                {"challengeId":"%s","code":"%s","password":"Encore2025"}""".formatted(challenge, code)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(oldSession)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(oldSession)).header("X-Bereta-Refresh", "1"))
                .andExpect(status().isUnauthorized());
        assertThat(CapturingNotifications.SMS.getLast().text()).contains("mot de passe vient d’être modifié");
        perform(jsonPost("/api/auth/login", loginBody(user.getEmail(), "Nouveau2025"))).andExpect(status().isOk());
    }

    @Test
    void forgotPasswordByEmailSendsSingleUseLink() throws Exception {
        UserAccountEntity user = activeUser();
        perform(jsonPost("/api/auth/password/forgot", "{\"identifier\":\"%s\"}".formatted(user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("EMAIL"));
        String body = CapturingNotifications.EMAILS.getLast().text();
        String token = body.substring(body.indexOf("token=") + 6).split("\\s")[0];

        perform(jsonPost("/api/auth/password/reset", "{\"token\":\"%s\",\"password\":\"Lien2025ok\"}".formatted(token)))
                .andExpect(status().isOk());
        perform(jsonPost("/api/auth/password/reset", "{\"token\":\"%s\",\"password\":\"Lien2025ok\"}".formatted(token)))
                .andExpect(status().isGone());
        assertThat(passwordEncoder.matches("Lien2025ok", users.findById(user.getId()).orElseThrow().getPasswordHash()))
                .isTrue();
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeUsingTheApi() throws Exception {
        UserAccountEntity user = activeUser();
        UserAccountEntity locked = users.findById(user.getId()).orElseThrow();
        locked.setTemporaryPasswordHash(passwordEncoder.encode("Provisoire1"));
        users.save(locked);

        MvcResult session = perform(jsonPost("/api/auth/login/verify", verifyBody(
                startLogin(user, "Provisoire1"), CapturingNotifications.lastCodeSentTo(user.getPhoneE164()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true)).andReturn();
        String access = accessToken(session);
        mockMvc.perform(get("/api/associations").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        MvcResult changed = mockMvc.perform(post("/api/auth/password/change").header("Authorization", "Bearer " + access)
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Provisoire1\",\"newPassword\":\"Personnel2025\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false)).andReturn();
        mockMvc.perform(get("/api/associations").header("Authorization", "Bearer " + accessToken(changed)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/associations").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    // ---- Outils ------------------------------------------------------------------------------

    private UserAccountEntity activeUser() {
        String phone = uniquePhone();
        UserAccountEntity user = new UserAccountEntity(uniqueEmail(), phone, "Compte actif",
                passwordEncoder.encode(PASSWORD), "RESPONSABLE", AccountStatus.ACTIVE);
        user.verifyPhone(phone);
        return users.save(user);
    }

    private String startLogin(UserAccountEntity user) throws Exception {
        return startLogin(user, PASSWORD);
    }

    private String startLogin(UserAccountEntity user, String password) throws Exception {
        return json(postJson("/api/auth/login", loginBody(user.getEmail(), password)).andExpect(status().isOk()))
                .get("challengeId").asText();
    }

    private MvcResult completeLogin(UserAccountEntity user) throws Exception {
        String challenge = startLogin(user);
        return perform(jsonPost("/api/auth/login/verify",
                verifyBody(challenge, CapturingNotifications.lastCodeSentTo(user.getPhoneE164()))))
                .andExpect(status().isOk()).andReturn();
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder jsonPost(String path,
                                                                                               String body) {
        return post(path).contentType(APPLICATION_JSON).content(body);
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(jsonPost(path, body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("bereta_refresh");
        assertThat(cookie).as("cookie de rafraîchissement").isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private static String loginBody(String identifier, String password) {
        return "{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password);
    }

    private static String verifyBody(String challengeId, String code) {
        return "{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challengeId, code);
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
        return "auth-" + java.util.UUID.randomUUID() + "@example.test";
    }
}
