package com.bereta.funds;

import com.bereta.TestSecurity;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bereta.members.MemberRepository;
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
class CashRemittanceControllerTest {

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
                "remittance-manager@example.test", "RESPONSABLE");
        treasurer = TestSecurity.account(userAccountRepository, passwordEncoder,
                "remittance-treasurer@example.test", "TRESORIER");
        TestSecurity.association(associationRepository, associationMembershipRepository, manager,
                "Association remises tests " + java.util.UUID.randomUUID());
    }

    @Test
    void declarationAndConfirmationMoveBalanceWithoutChangingCollectedTotalAndCannotConfirmTwice() throws Exception {
        TestFunds funds = createFunds("100.00");
        recordPayment(funds.enrollmentId(), manager, "60.00");
        String remittanceId = createRemittance(funds.localFundId(), treasurer, "40.00");

        mockMvc.perform(as(manager, get("/api/caisses-principales/{id}/remises/synthese", funds.primaryFundId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCollected").value(60))
                .andExpect(jsonPath("$.availableLocally").value(20))
                .andExpect(jsonPath("$.inTransit").value(40))
                .andExpect(jsonPath("$.centralBalance").value(0))
                .andExpect(jsonPath("$.totalHeldGlobally").value(60));

        String confirmPath = "/api/caisses-principales/%s/remises/%s/confirmer"
                .formatted(funds.primaryFundId(), remittanceId);
        mockMvc.perform(as(manager, post(confirmPath)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMEE"))
                .andExpect(jsonPath("$.confirmedByName").value("Compte de test"));
        mockMvc.perform(as(manager, post(confirmPath))).andExpect(status().isConflict());

        mockMvc.perform(as(manager, get("/api/caisses-principales/{id}/remises/synthese", funds.primaryFundId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCollected").value(60))
                .andExpect(jsonPath("$.availableLocally").value(20))
                .andExpect(jsonPath("$.inTransit").value(0))
                .andExpect(jsonPath("$.centralBalance").value(40))
                .andExpect(jsonPath("$.totalHeldGlobally").value(60));
        mockMvc.perform(as(manager, get("/api/paiements/synthese")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.collectedAmount").value(60));
    }

    @Test
    void rejectsRemittanceAboveAvailableAndTreasurerCannotConfirm() throws Exception {
        TestFunds funds = createFunds("100.00");
        recordPayment(funds.enrollmentId(), manager, "15.00");
        mockMvc.perform(as(treasurer, post("/api/caisses-locales/{id}/remises", funds.localFundId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":15.01,\"transferredOn\":\"2026-10-28\",\"method\":\"ESPECES\"}")))
                .andExpect(status().isBadRequest());

        String remittanceId = createRemittance(funds.localFundId(), treasurer, "10.00");
        mockMvc.perform(as(treasurer, post("/api/caisses-principales/{primaryId}/remises/{id}/confirmer",
                        funds.primaryFundId(), remittanceId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancellationRestoresLocalAvailabilityAndKeepsAuditFields() throws Exception {
        TestFunds funds = createFunds("50.00");
        recordPayment(funds.enrollmentId(), manager, "25.00");
        String remittanceId = createRemittance(funds.localFundId(), treasurer, "20.00");

        mockMvc.perform(as(manager, post("/api/caisses-principales/{primaryId}/remises/{id}/annuler",
                        funds.primaryFundId(), remittanceId).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"Montant à recompter\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANNULEE"))
                .andExpect(jsonPath("$.cancelledByName").value("Compte de test"))
                .andExpect(jsonPath("$.cancellationReason").value("Montant à recompter"));
        mockMvc.perform(as(manager, get("/api/caisses-principales/{id}/remises/synthese", funds.primaryFundId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableLocally").value(25))
                .andExpect(jsonPath("$.inTransit").value(0))
                .andExpect(jsonPath("$.totalHeldGlobally").value(25));
    }

    private TestFunds createFunds(String expectedAmount) throws Exception {
        String primaryId = create("/api/caisses-principales", """
                {"name":"Caisse centrale test","objective":"Tester les remises"}
                """, manager);
        String localId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse locale test\"}", manager);
        mockMvc.perform(as(manager, post("/api/caisses-principales/{id}/acces", primaryId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"phone\":\"" + TestSecurity.phoneFor("remittance-treasurer@example.test") + "\",\"localFundId\":\"%s\"}"
                                .formatted(localId))))
                .andExpect(status().isCreated());
        String campaignId = create("/api/campagnes", """
                {"primaryFundId":"%s","name":"Campagne test","campaignType":"ANNUAL","campaignYear":2026,"expectedAmount":%s}
                """.formatted(primaryId, expectedAmount), manager);
        String memberId = create("/api/membres", """
                {"firstName":"Membre","lastName":"Test","localFundId":"%s"}
                """.formatted(localId), manager);
        String enrollmentId = create("/api/membres/%s/inscriptions".formatted(memberId),
                "{\"campaignId\":\"%s\",\"localFundId\":\"%s\"}".formatted(campaignId, localId), manager);
        return new TestFunds(primaryId, localId, enrollmentId);
    }

    private void recordPayment(String enrollmentId, UserAccountEntity actor, String amount) throws Exception {
        mockMvc.perform(as(actor, post("/api/paiements/inscriptions/{id}", enrollmentId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":%s,\"paidOn\":\"2026-10-27\",\"paymentMethod\":\"ESPECES\"}"
                                .formatted(amount))))
                .andExpect(status().isCreated());
    }

    private String createRemittance(String localFundId, UserAccountEntity actor, String amount) throws Exception {
        var result = mockMvc.perform(as(actor, post("/api/caisses-locales/{id}/remises", localFundId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":%s,\"transferredOn\":\"2026-10-28\",\"method\":\"ESPECES\"}"
                                .formatted(amount))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EN_ATTENTE"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
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

    private record TestFunds(String primaryFundId, String localFundId, String enrollmentId) { }
}
