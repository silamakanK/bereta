package com.bereta.members;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.bereta.funds.MemberCampaignRepository;
import com.bereta.funds.FundMembershipRepository;
import com.bereta.funds.PrimaryFundRepository;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.LocalFundRepository;
import com.bereta.funds.PaymentRepository;
import com.bereta.funds.CashRemittanceRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberCampaignRepository memberCampaignRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CashRemittanceRepository remittanceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PrimaryFundRepository primaryFundRepository;
    @Autowired private FundMembershipRepository fundMembershipRepository;
    @Autowired private LocalFundRepository localFundRepository;
    @Autowired private AssociationRepository associationRepository;
    @Autowired private AssociationMembershipRepository associationMembershipRepository;
    private UserAccountEntity testAccount;
    private LocalFundEntity testLocalFund;

    @BeforeEach
    void clearMembers() {
        remittanceRepository.deleteAll();
        paymentRepository.deleteAll();
        memberCampaignRepository.deleteAll();
        memberRepository.deleteAll();
        testAccount = TestSecurity.account(userAccountRepository, passwordEncoder,
                "member-tests@example.test", "RESPONSABLE");
        var primaryFund = TestSecurity.primaryFund(primaryFundRepository, fundMembershipRepository,
                associationRepository, associationMembershipRepository, testAccount, "Membres tests " + java.util.UUID.randomUUID());
        testLocalFund = localFundRepository.save(new LocalFundEntity(primaryFund, "Caisse de test"));
    }

    @Test
    void managerAssignsAMemberToThePrimaryFundOrToALocalFundAndTheListShowsIt() throws Exception {
        mockMvc.perform(secured(post("/api/membres").contentType(APPLICATION_JSON)
                        .content("{\"firstName\":\"Awa\",\"lastName\":\"Principale\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inPrimaryFund").value(true))
                .andExpect(jsonPath("$.localFundId").doesNotExist())
                .andExpect(jsonPath("$.fundName").value("Caisse principale"));
        mockMvc.perform(secured(post("/api/membres").contentType(APPLICATION_JSON)
                        .content("{\"firstName\":\"Binta\",\"lastName\":\"Locale\",\"localFundId\":\"%s\"}"
                                .formatted(testLocalFund.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inPrimaryFund").value(false))
                .andExpect(jsonPath("$.fundName").value("Caisse de test"));
        mockMvc.perform(secured(get("/api/membres")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.lastName == 'Principale')].fundName").value("Caisse principale"))
                .andExpect(jsonPath("$[?(@.lastName == 'Locale')].localFundId").value(testLocalFund.getId().toString()));

        // Un trésorier ne peut rattacher qu'à sa caisse locale, et ne voit pas les membres de la caisse principale.
        UserAccountEntity treasurer = TestSecurity.account(userAccountRepository, passwordEncoder,
                "member-treasurer-" + java.util.UUID.randomUUID() + "@example.test", "TRESORIER");
        fundMembershipRepository.save(new com.bereta.funds.FundMembershipEntity(treasurer,
                testLocalFund.getPrimaryFund(), testLocalFund, com.bereta.funds.FundRole.TRESORIER));
        mockMvc.perform(post("/api/membres").with(TestSecurity.as(treasurer)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"firstName\":\"Sans\",\"lastName\":\"Caisse\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/membres").with(TestSecurity.as(treasurer)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lastName").value("Locale"));
    }

    @Test
    void listsMembersAndCreatesOneWithOptionalPhone() throws Exception {
        mockMvc.perform(secured(get("/api/membres")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(secured(post("/api/membres")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"firstName":"  Awa ","lastName":" Traoré ","phone":" ","localFundId":"%s"}
                                """.formatted(testLocalFund.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Awa"))
                .andExpect(jsonPath("$.lastName").value("Traoré"))
                .andExpect(jsonPath("$.phone").doesNotExist());

        mockMvc.perform(secured(get("/api/membres")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Awa"));
    }

    @Test
    void rejectsMemberWithoutRequiredName() throws Exception {
        mockMvc.perform(secured(post("/api/membres")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"firstName":" ","lastName":"Traoré","localFundId":"%s"}
                                """.formatted(testLocalFund.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesAnExistingMember() throws Exception {
        MvcResult created = mockMvc.perform(secured(post("/api/membres")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"firstName":"Awa","lastName":"Traoré","phone":"0612345678","localFundId":"%s"}
                                """.formatted(testLocalFund.getId()))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode member = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = member.get("id").asText();

        mockMvc.perform(secured(put("/api/membres/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"firstName":"Awa Mariam","lastName":"Traoré","phone":"  "}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Awa Mariam"))
                .andExpect(jsonPath("$.phone").doesNotExist());

        mockMvc.perform(secured(get("/api/membres/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Awa Mariam"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder secured(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.with(TestSecurity.as(testAccount)).with(csrf());
    }
}
