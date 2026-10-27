package com.bereta.members;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.bereta.funds.MemberCampaignRepository;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberCampaignRepository memberCampaignRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearMembers() {
        memberCampaignRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void listsMembersAndCreatesOneWithOptionalPhone() throws Exception {
        mockMvc.perform(get("/api/membres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post("/api/membres")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"firstName":"  Awa ","lastName":" Traoré ","phone":" "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Awa"))
                .andExpect(jsonPath("$.lastName").value("Traoré"))
                .andExpect(jsonPath("$.phone").doesNotExist());

        mockMvc.perform(get("/api/membres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Awa"));
    }

    @Test
    void rejectsMemberWithoutRequiredName() throws Exception {
        mockMvc.perform(post("/api/membres")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"firstName":" ","lastName":"Traoré"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesAnExistingMember() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/membres")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"firstName":"Awa","lastName":"Traoré","phone":"0612345678"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode member = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = member.get("id").asText();

        mockMvc.perform(put("/api/membres/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"firstName":"Awa Mariam","lastName":"Traoré","phone":"  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Awa Mariam"))
                .andExpect(jsonPath("$.phone").doesNotExist());

        mockMvc.perform(get("/api/membres/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Awa Mariam"));
    }
}
