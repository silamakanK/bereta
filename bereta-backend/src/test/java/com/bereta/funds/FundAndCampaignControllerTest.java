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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class FundAndCampaignControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private LocalFundRepository localFundRepository;
    @Autowired private PrimaryFundRepository primaryFundRepository;

    @BeforeEach
    void clearConfiguration() {
        campaignRepository.deleteAll();
        localFundRepository.deleteAll();
        primaryFundRepository.deleteAll();
    }

    @Test
    void createsPrimaryAndLocalFundsAndAnnualCampaign() throws Exception {
        MvcResult fundResult = mockMvc.perform(post("/api/caisses-principales")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Développement de Niogoméra","objective":"Financer les projets du village"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Développement de Niogoméra"))
                .andReturn();
        JsonNode fund = objectMapper.readTree(fundResult.getResponse().getContentAsString());
        String fundId = fund.get("id").asText();

        mockMvc.perform(post("/api/caisses-principales/{id}/caisses-locales", fundId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Caisse de Mamadou\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primaryFundId").value(fundId));

        mockMvc.perform(post("/api/campagnes")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"primaryFundId":"%s","name":"Campagne 2026","campaignType":"ANNUAL","campaignYear":2026,"expectedAmount":30.00}
                                """.formatted(fundId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expectedAmount").value(30.00))
                .andExpect(jsonPath("$.campaignYear").value(2026));

        mockMvc.perform(get("/api/campagnes").queryParam("caissePrincipaleId", fundId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Campagne 2026"));
    }

    @Test
    void eventCampaignCanHaveUnconfirmedAmountAndNoDueDate() throws Exception {
        MvcResult fundResult = mockMvc.perform(post("/api/caisses-principales")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Événements des zawiyas","objective":"Financer les événements"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String fundId = objectMapper.readTree(fundResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/campagnes")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"primaryFundId":"%s","name":"Rencontre annuelle","campaignType":"EVENT"}
                                """.formatted(fundId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expectedAmount").doesNotExist())
                .andExpect(jsonPath("$.dueDate").doesNotExist());
    }

    @Test
    void annualCampaignRequiresYearAndExpectedAmount() throws Exception {
        MvcResult fundResult = mockMvc.perform(post("/api/caisses-principales")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Solidarité","objective":"Soutenir les membres"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String fundId = objectMapper.readTree(fundResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/campagnes")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"primaryFundId":"%s","name":"Campagne sans montant","campaignType":"ANNUAL","campaignYear":2026}
                                """.formatted(fundId)))
                .andExpect(status().isBadRequest());
    }
}
