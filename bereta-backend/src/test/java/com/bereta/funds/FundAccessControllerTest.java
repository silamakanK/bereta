package com.bereta.funds;

import com.bereta.TestSecurity;
import com.bereta.auth.BeretaUserDetails;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FundAccessControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PrimaryFundRepository primaryFundRepository;
    @Autowired private LocalFundRepository localFundRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberCampaignRepository enrollmentRepository;
    @Autowired private FundMembershipRepository membershipRepository;
    @Autowired private FundInvitationRepository invitationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CashRemittanceRepository remittanceRepository;
    @Autowired private AssociationRepository associationRepository;
    @Autowired private AssociationMembershipRepository associationMembershipRepository;

    private UserAccountEntity manager;
    private UserAccountEntity treasurer;
    private UserAccountEntity outsider;

    @BeforeEach
    void setUp() {
        remittanceRepository.deleteAll();
        paymentRepository.deleteAll();
        enrollmentRepository.deleteAll();
        campaignRepository.deleteAll();
        memberRepository.deleteAll();
        invitationRepository.deleteAll();
        membershipRepository.deleteAll();
        localFundRepository.deleteAll();
        primaryFundRepository.deleteAll();
        manager = TestSecurity.account(userAccountRepository, passwordEncoder,
                "access-manager@example.test", "RESPONSABLE");
        treasurer = TestSecurity.account(userAccountRepository, passwordEncoder,
                "access-treasurer@example.test", "TRESORIER");
        outsider = TestSecurity.account(userAccountRepository, passwordEncoder,
                "access-outsider@example.test", "TRESORIER");
        TestSecurity.association(associationRepository, associationMembershipRepository, manager,
                "Association accès tests " + java.util.UUID.randomUUID());
    }

    @Test
    void treasurerOnlySeesAssignedLocalFundAndItsEnrolledMembers() throws Exception {
        String primaryId = create("/api/caisses-principales", """
                {"name":"Développement","objective":"Projets du village"}
                """, manager);
        String assignedLocalId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse A\"}", manager);
        String otherLocalId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse B\"}", manager);
        create("/api/caisses-principales/%s/acces".formatted(primaryId),
                "{\"email\":\"access-treasurer@example.test\",\"localFundId\":\"%s\"}".formatted(assignedLocalId), manager);
        String campaignId = create("/api/campagnes", """
                {"primaryFundId":"%s","name":"Cotisation 2026","campaignType":"ANNUAL","campaignYear":2026,"expectedAmount":30}
                """.formatted(primaryId), manager);
        String memberId = create("/api/membres", """
                {"firstName":"Awa","lastName":"Traoré","localFundId":"%s"}
                """.formatted(assignedLocalId), manager);
        create("/api/membres/%s/inscriptions".formatted(memberId),
                "{\"campaignId\":\"%s\",\"localFundId\":\"%s\"}".formatted(campaignId, assignedLocalId), manager);
        String otherCampaignId = create("/api/campagnes", """
                {"primaryFundId":"%s","name":"Événement","campaignType":"EVENT"}
                """.formatted(primaryId), manager);
        create("/api/membres/%s/inscriptions".formatted(memberId),
                "{\"campaignId\":\"%s\",\"localFundId\":\"%s\"}".formatted(otherCampaignId, otherLocalId), manager);

        mockMvc.perform(as(treasurer, get("/api/caisses-principales")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(as(treasurer, get("/api/caisses-principales/{id}/caisses-locales", primaryId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(assignedLocalId));
        mockMvc.perform(as(treasurer, get("/api/membres")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(memberId));
        mockMvc.perform(as(treasurer, get("/api/membres/{id}/inscriptions", memberId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].localFundId").value(assignedLocalId));
        mockMvc.perform(as(treasurer, get("/api/caisses-principales/{id}/caisses-locales/{localId}", primaryId, otherLocalId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(treasurer, get("/api/caisses-principales/{id}/acces", primaryId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(treasurer,
                        post("/api/membres/{memberId}/inscriptions", memberId).contentType(APPLICATION_JSON)
                                .content("{\"campaignId\":\"%s\",\"localFundId\":\"%s\"}"
                                        .formatted(otherCampaignId, otherLocalId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void preventsUnassignedAccountFromReadingAnotherFund() throws Exception {
        String primaryId = create("/api/caisses-principales", """
                {"name":"Solidarité","objective":"Soutenir les membres"}
                """, manager);
        String localId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse test\"}", manager);
        String memberId = create("/api/membres", """
                {"firstName":"Membre","lastName":"Privé","localFundId":"%s"}
                """.formatted(localId), manager);

        mockMvc.perform(as(outsider, get("/api/caisses-principales/{id}", primaryId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(outsider, get("/api/campagnes").queryParam("caissePrincipaleId", primaryId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(outsider, get("/api/membres/{id}", memberId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(outsider, post("/api/membres").contentType(APPLICATION_JSON)
                        .content("{\"firstName\":\"Sans\",\"lastName\":\"Accès\",\"localFundId\":\"%s\"}"
                                .formatted(localId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void treasurerCannotCreateCampaignOrManageAnotherTreasurer() throws Exception {
        String primaryId = create("/api/caisses-principales", """
                {"name":"Événements","objective":"Financer les événements"}
                """, manager);
        String localId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse A\"}", manager);
        create("/api/caisses-principales/%s/acces".formatted(primaryId),
                "{\"email\":\"access-treasurer@example.test\",\"localFundId\":\"%s\"}".formatted(localId), manager);

        mockMvc.perform(as(treasurer, post("/api/campagnes").contentType(APPLICATION_JSON).content("""
                        {"primaryFundId":"%s","name":"Événement","campaignType":"EVENT"}
                        """.formatted(primaryId))))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(treasurer, get("/api/caisses-principales/{id}/acces", primaryId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitationCreatesScopedTreasurerAccountAndCanBeUsedOnce() throws Exception {
        String primaryId = create("/api/caisses-principales", """
                {"name":"Solidarité","objective":"Soutenir les membres"}
                """, manager);
        String localId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse d’Awa\"}", manager);
        var invitationResult = mockMvc.perform(as(manager,
                        post("/api/caisses-principales/{id}/acces/invitations", primaryId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"email\":\"new-treasurer@example.test\",\"localFundId\":\"%s\"}"
                                        .formatted(localId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new-treasurer@example.test"))
                .andReturn();
        String token = objectMapper.readTree(invitationResult.getResponse().getContentAsString())
                .get("token").asText();
        String acceptBody = ("{\"token\":\"%s\",\"email\":\"new-treasurer@example.test\","
                + "\"displayName\":\"Awa Test\",\"password\":\"Awa123\"}").formatted(token);

        mockMvc.perform(post("/api/auth/invitations/accept").with(csrf())
                        .contentType(APPLICATION_JSON).content(acceptBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new-treasurer@example.test"))
                .andExpect(jsonPath("$.role").value("TRESORIER"));
        mockMvc.perform(post("/api/auth/invitations/accept").with(csrf())
                        .contentType(APPLICATION_JSON).content(acceptBody))
                .andExpect(status().isNotFound());

        UserAccountEntity invited = userAccountRepository.findByEmailIgnoreCase("new-treasurer@example.test")
                .orElseThrow();
        mockMvc.perform(as(invited, get("/api/caisses-principales/{id}/caisses-locales", primaryId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(localId));
    }

    @Test
    void managerCanDeactivateTreasurerAccess() throws Exception {
        String primaryId = create("/api/caisses-principales", """
                {"name":"Caisse test","objective":"Tester une révocation"}
                """, manager);
        String localId = create("/api/caisses-principales/%s/caisses-locales".formatted(primaryId),
                "{\"name\":\"Caisse locale test\"}", manager);
        var assigned = mockMvc.perform(as(manager,
                        post("/api/caisses-principales/{id}/acces", primaryId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"email\":\"access-treasurer@example.test\",\"localFundId\":\"%s\"}"
                                        .formatted(localId))))
                .andExpect(status().isCreated()).andReturn();
        String membershipId = objectMapper.readTree(assigned.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(as(manager,
                        delete("/api/caisses-principales/{fundId}/acces/{membershipId}", primaryId, membershipId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(treasurer, get("/api/caisses-principales/{id}", primaryId)))
                .andExpect(status().isForbidden());
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
