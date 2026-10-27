package com.bereta.funds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class MemberCampaignControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberCampaignRepository enrollmentRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private LocalFundRepository localFundRepository;
    @Autowired private PrimaryFundRepository primaryFundRepository;
    @Autowired private com.bereta.members.MemberRepository memberRepository;

    @BeforeEach
    void clearData() {
        enrollmentRepository.deleteAll();
        campaignRepository.deleteAll();
        localFundRepository.deleteAll();
        primaryFundRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void enrollsMemberAndPreventsDuplicateCampaignEnrollment() throws Exception {
        String memberId = create("/api/membres", """
                {"firstName":"Awa","lastName":"Traoré"}
                """);
        String primaryFundId = create("/api/caisses-principales", """
                {"name":"Développement","objective":"Projets du village"}
                """);
        String localFundId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryFundId),
                "{\"name\":\"Caisse de Mamadou\"}");
        String campaignId = create("/api/campagnes", """
                {"primaryFundId":"%s","name":"Campagne 2026","campaignType":"ANNUAL","campaignYear":2026,"expectedAmount":30.00}
                """.formatted(primaryFundId));

        mockMvc.perform(post("/api/membres/{memberId}/inscriptions", memberId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"campaignId":"%s","localFundId":"%s"}
                                """.formatted(campaignId, localFundId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaignName").value("Campagne 2026"))
                .andExpect(jsonPath("$.localFundName").value("Caisse de Mamadou"))
                .andExpect(jsonPath("$.expectedAmount").value(30.00));

        mockMvc.perform(post("/api/membres/{memberId}/inscriptions", memberId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"campaignId":"%s","localFundId":"%s"}
                                """.formatted(campaignId, localFundId)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsLocalFundFromDifferentPrimaryFund() throws Exception {
        String memberId = create("/api/membres", """
                {"firstName":"Mamadou","lastName":"Cissé"}
                """);
        String firstFundId = create("/api/caisses-principales", """
                {"name":"Solidarité","objective":"Soutenir les membres"}
                """);
        String secondFundId = create("/api/caisses-principales", """
                {"name":"Développement","objective":"Projets du village"}
                """);
        String localFundId = create("/api/caisses-principales/%s/caisses-locales".formatted(firstFundId),
                "{\"name\":\"Caisse locale A\"}");
        String campaignId = create("/api/campagnes", """
                {"primaryFundId":"%s","name":"Année 2026","campaignType":"ANNUAL","campaignYear":2026,"expectedAmount":18.00}
                """.formatted(secondFundId));

        mockMvc.perform(post("/api/membres/{memberId}/inscriptions", memberId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"campaignId":"%s","localFundId":"%s"}
                                """.formatted(campaignId, localFundId)))
                .andExpect(status().isBadRequest());
    }

    private String create(String path, String content) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }
}
