package com.bereta.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class BonjourControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bonjourEndpointReturnsGreeting() throws Exception {
        mockMvc.perform(get("/api/bonjour"))
                .andExpect(status().isOk())
                .andExpect(content().string("Bonjour depuis BERETA !"));
    }
}
