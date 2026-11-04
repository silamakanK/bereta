package com.bereta.funds;

import com.bereta.TestSecurity;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationRepository;
import com.bereta.members.MemberRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PrimaryFundRepository primaryFundRepository;
    @Autowired private LocalFundRepository localFundRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberCampaignRepository enrollmentRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CashRemittanceRepository remittanceRepository;
    @Autowired private FundMembershipRepository membershipRepository;
    @Autowired private AssociationRepository associationRepository;
    @Autowired private AssociationMembershipRepository associationMembershipRepository;

    private UserAccountEntity manager;
    private UserAccountEntity treasurer;

    @BeforeEach
    void setUp() {
        remittanceRepository.deleteAll();
        paymentRepository.deleteAll();
        enrollmentRepository.deleteAll();
        campaignRepository.deleteAll();
        memberRepository.deleteAll();
        membershipRepository.deleteAll();
        localFundRepository.deleteAll();
        primaryFundRepository.deleteAll();
        manager = TestSecurity.account(userAccountRepository, passwordEncoder,
                "payment-manager@example.test", "RESPONSABLE");
        treasurer = TestSecurity.account(userAccountRepository, passwordEncoder,
                "payment-treasurer@example.test", "TRESORIER");
        TestSecurity.association(associationRepository, associationMembershipRepository, manager,
                "Association paiement tests " + java.util.UUID.randomUUID());
    }

    @Test
    void recordsPartialPaymentAndReturnsScopedBalanceAndHistory() throws Exception {
        String primaryId = create("/api/caisses-principales", """
                {"name":"Caisse paiement","objective":"Test paiements"}
                """, manager);
        String localId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse locale\"}", manager);
        String campaignId = create("/api/campagnes", """
                {"primaryFundId":"%s","name":"Cotisation 2026","campaignType":"ANNUAL","campaignYear":2026,"expectedAmount":30}
                """.formatted(primaryId), manager);
        String memberId = create("/api/membres", """
                {"firstName":"Awa","lastName":"Traoré","localFundId":"%s"}
                """.formatted(localId), manager);
        String enrollmentId = create("/api/membres/%s/inscriptions".formatted(memberId),
                "{\"campaignId\":\"%s\",\"localFundId\":\"%s\"}".formatted(campaignId, localId), manager);

        mockMvc.perform(as(manager, post("/api/paiements/inscriptions/{id}", enrollmentId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":10.50,\"paidOn\":\"2026-10-01\",\"paymentMethod\":\"ESPECES\",\"note\":\"Premier versement\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(10.50))
                .andExpect(jsonPath("$.recordedByName").value("Compte de test"));

        mockMvc.perform(as(manager, get("/api/paiements/inscriptions-a-payer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paidAmount").value(10.50))
                .andExpect(jsonPath("$[0].remainingAmount").value(19.50))
                .andExpect(jsonPath("$[0].status").value("PARTIELLEMENT_PAYE"));
        mockMvc.perform(as(manager, get("/api/paiements/synthese")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectedAmount").value(10.50))
                .andExpect(jsonPath("$.remainingAmount").value(19.50));
        mockMvc.perform(as(manager, get("/api/paiements")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberName").value("Awa Traoré"));
    }

    @Test
    void rejectsOverpaymentAndBlocksTreasurerOutsideAssignedLocalFund() throws Exception {
        String primaryId = create("/api/caisses-principales", """
                {"name":"Caisse protégée","objective":"Test droits paiement"}
                """, manager);
        String assignedLocalId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse autorisée\"}", manager);
        String otherLocalId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse privée\"}", manager);
        mockMvc.perform(as(manager, post("/api/caisses-principales/{id}/acces", primaryId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"payment-treasurer@example.test\",\"localFundId\":\"%s\"}".formatted(assignedLocalId))))
                .andExpect(status().isCreated());
        String campaignId = create("/api/campagnes", """
                {"primaryFundId":"%s","name":"Campagne","campaignType":"ANNUAL","campaignYear":2026,"expectedAmount":20}
                """.formatted(primaryId), manager);
        String memberId = create("/api/membres", """
                {"firstName":"Membre","lastName":"Privé","localFundId":"%s"}
                """.formatted(otherLocalId), manager);
        String enrollmentId = create("/api/membres/%s/inscriptions".formatted(memberId),
                "{\"campaignId\":\"%s\",\"localFundId\":\"%s\"}".formatted(campaignId, otherLocalId), manager);

        mockMvc.perform(as(manager, post("/api/paiements/inscriptions/{id}", enrollmentId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":20.01,\"paidOn\":\"2026-10-01\",\"paymentMethod\":\"VIREMENT\"}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(treasurer, post("/api/paiements/inscriptions/{id}", enrollmentId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":10,\"paidOn\":\"2026-10-01\",\"paymentMethod\":\"VIREMENT\"}")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(treasurer, get("/api/paiements")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    private String create(String path, String content, UserAccountEntity actor) throws Exception {
        var result = mockMvc.perform(as(actor, post(path).contentType(APPLICATION_JSON).content(content)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }

    private MockHttpServletRequestBuilder as(UserAccountEntity actor, MockHttpServletRequestBuilder request) {
        return request.with(TestSecurity.as(actor)).with(csrf());
    }
}
