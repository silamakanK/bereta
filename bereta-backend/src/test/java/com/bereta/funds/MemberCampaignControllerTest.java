package com.bereta.funds;

import com.bereta.TestSecurity;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberCampaignControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberCampaignRepository enrollmentRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private LocalFundRepository localFundRepository;
    @Autowired private PrimaryFundRepository primaryFundRepository;
    @Autowired private com.bereta.members.MemberRepository memberRepository;
    @Autowired private FundMembershipRepository fundMembershipRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CashRemittanceRepository remittanceRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AssociationRepository associationRepository;
    @Autowired private AssociationMembershipRepository associationMembershipRepository;
    private UserAccountEntity testAccount;

    @BeforeEach
    void clearData() {
        remittanceRepository.deleteAll();
        paymentRepository.deleteAll();
        enrollmentRepository.deleteAll();
        campaignRepository.deleteAll();
        memberRepository.deleteAll();
        fundMembershipRepository.deleteAll();
        localFundRepository.deleteAll();
        primaryFundRepository.deleteAll();
        testAccount = TestSecurity.account(userAccountRepository, passwordEncoder,
                "enrollment-tests@example.test", "RESPONSABLE");
        TestSecurity.association(associationRepository, associationMembershipRepository, testAccount,
                "Association enrollment tests " + java.util.UUID.randomUUID());
    }

    @Test
    void enrollsMemberAndPreventsDuplicateCampaignEnrollment() throws Exception {
        String primaryFundId = create("/api/caisses-principales", """
                {"name":"Développement","objective":"Projets du village"}
                """);
        String localFundId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryFundId),
                "{\"name\":\"Caisse de Mamadou\"}");
        // Campagne créée avant le membre : pas d'inscription automatique, on l'inscrit à la main.
        String campaignId = create("/api/campagnes", """
                {"primaryFundId":"%s","name":"Campagne 2026","campaignType":"ANNUAL","campaignYear":2026,"expectedAmount":30.00}
                """.formatted(primaryFundId));
        String memberId = create("/api/membres", """
                {"firstName":"Awa","lastName":"Traoré","localFundId":"%s"}
                """.formatted(localFundId));

        mockMvc.perform(secured(post("/api/membres/{memberId}/inscriptions", memberId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"campaignId":"%s","localFundId":"%s"}
                                """.formatted(campaignId, localFundId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaignName").value("Campagne 2026"))
                .andExpect(jsonPath("$.localFundName").value("Caisse de Mamadou"))
                .andExpect(jsonPath("$.expectedAmount").value(30.00));

        mockMvc.perform(secured(post("/api/membres/{memberId}/inscriptions", memberId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"campaignId":"%s","localFundId":"%s"}
                                """.formatted(campaignId, localFundId))))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesASecondPrimaryFundInTheSameAssociation() throws Exception {
        create("/api/caisses-principales", """
                {"name":"Solidarité","objective":"Soutenir les membres"}
                """);
        mockMvc.perform(secured(post("/api/caisses-principales")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Développement\",\"objective\":\"Projets du village\"}")))
                .andExpect(status().isConflict());
    }

    private String create(String path, String content) throws Exception {
        MvcResult result = mockMvc.perform(secured(post(path)
                        .contentType(APPLICATION_JSON)
                        .content(content)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder secured(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.with(TestSecurity.as(testAccount)).with(csrf());
    }
}
