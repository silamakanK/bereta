package com.bereta.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.bereta.funds.FundMembershipRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private FundMembershipRepository fundMembershipRepository;

    @BeforeEach
    void setUpUser() {
        userAccountRepository.findByEmailIgnoreCase("responsable@example.test").orElseGet(() ->
                userAccountRepository.save(new UserAccountEntity(
                        "responsable@example.test", "Responsable Test",
                        passwordEncoder.encode("TestPassword!234"), "RESPONSABLE")));
    }

    @Test
    void protectsApplicationApiUntilLogin() throws Exception {
        mockMvc.perform(get("/api/membres"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logsInAndPersistsTheSessionForLaterRequests() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", "responsable@example.test")
                        .param("password", "TestPassword!234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("responsable@example.test"))
                .andExpect(jsonPath("$.role").value("RESPONSABLE"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Responsable Test"));
    }

    @Test
    void rejectsIncorrectPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", "responsable@example.test")
                        .param("password", "incorrect-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void selfRegistrationCreatesOnlyAnAccountAndThatAccountCanLogInWithoutCaisse() throws Exception {
        String email = "nouveau-" + java.util.UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON).content("""
                        {"firstName":"Awa","lastName":"Traoré","email":"%s","password":"Awa123"}
                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Awa Traoré"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        UserAccountEntity account = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(passwordEncoder.matches("Awa123", account.getPasswordHash()));
        assertTrue(fundMembershipRepository.findAllByUserAccountIdAndActiveTrueOrderByCreatedAtAsc(account.getId()).isEmpty());

        MvcResult login = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", email.toUpperCase(java.util.Locale.ROOT))
                        .param("password", "Awa123"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mockMvc.perform(get("/api/associations").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/membres").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCaseInsensitiveDuplicateAndWeakRegistration() throws Exception {
        String email = "duplicate-" + java.util.UUID.randomUUID() + "@example.test";
        String validRequest = """
                {"firstName":"Mamadou","lastName":"Test","email":"%s","password":"StrongPassword!234"}
                """.formatted(email);
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON).content(validRequest))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON)
                        .content(validRequest.replace(email, email.toUpperCase(java.util.Locale.ROOT))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON).content("""
                        {"firstName":"Awa","lastName":"Test","email":"short-%s@example.test","password":"short"}
                        """.formatted(java.util.UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON).content("""
                        {"firstName":"%s","lastName":"%s","email":"long-name-%s@example.test","password":"StrongPassword!234"}
                        """.formatted("A".repeat(70), "B".repeat(60), java.util.UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordDoesNotPretendToSendWhenEmailIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/auth/password/forgot").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"responsable@example.test\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
