package com.bereta.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @BeforeEach
    void setUpUser() {
        userAccountRepository.deleteAll();
        userAccountRepository.save(new UserAccountEntity(
                "responsable@example.test", "Responsable Test", passwordEncoder.encode("TestPassword!234"), "RESPONSABLE"));
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
}
