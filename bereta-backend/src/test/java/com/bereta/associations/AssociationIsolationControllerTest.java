package com.bereta.associations;

import com.bereta.TestSecurity;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.auth.BeretaUserDetails;
import com.bereta.funds.*;
import com.bereta.members.MemberEntity;
import com.bereta.members.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssociationIsolationControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository users;
    @Autowired private AssociationRepository associations;
    @Autowired private AssociationMembershipRepository associationMemberships;
    @Autowired private PrimaryFundRepository primaryFunds;
    @Autowired private LocalFundRepository localFunds;
    @Autowired private FundMembershipRepository fundMemberships;
    @Autowired private MemberRepository members;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void treasurerAccountCannotCreateAnAssociation() throws Exception {
        UserAccountEntity treasurer = TestSecurity.account(users, passwordEncoder,
                "association-treasurer-" + UUID.randomUUID() + "@test.local", "TRESORIER");
        mockMvc.perform(post("/api/associations").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(new BeretaUserDetails(treasurer))).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Association interdite\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCanCreateAssociationAndBecomesItsResponsible() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserAccountEntity manager = TestSecurity.account(users, passwordEncoder,
                "association-create-" + suffix + "@test.local", "RESPONSABLE");
        var result = mockMvc.perform(post("/api/associations").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(new BeretaUserDetails(manager))).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Association créée\",\"description\":\"Un espace indépendant\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentUserResponsible").value(true))
                .andReturn();
        var association = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
        UUID associationId = UUID.fromString(association.get("id").asText());
        UserAccountEntity secondManager = TestSecurity.account(users, passwordEncoder,
                "association-co-manager-" + suffix + "@test.local", "TRESORIER");
        mockMvc.perform(post("/api/associations/{id}/responsables", associationId)
                        .header("X-Association-Id", associationId.toString())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(new BeretaUserDetails(manager))).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + secondManager.getEmail() + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/associations/{id}/responsables", associationId)
                        .header("X-Association-Id", associationId.toString())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(new BeretaUserDetails(manager))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(post("/api/caisses-principales").header("X-Association-Id", associationId.toString())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(new BeretaUserDetails(manager))).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Caisse initiale\",\"objective\":\"Test\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void sameManagerSeesOnlyTheSelectedAssociationAndTreasurersStayLocal() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UserAccountEntity manager = TestSecurity.account(users, passwordEncoder, "manager-" + suffix + "@test.local", "RESPONSABLE");
        UserAccountEntity treasurer = TestSecurity.account(users, passwordEncoder, "treasurer-" + suffix + "@test.local", "TRESORIER");
        UserAccountEntity secondTreasurer = TestSecurity.account(users, passwordEncoder,
                "treasurer-two-" + suffix + "@test.local", "TRESORIER");
        AssociationEntity associationA = association("Association A " + suffix, manager);
        AssociationEntity associationB = association("Association B " + suffix, manager);

        PrimaryFundEntity fundA = primaryFunds.save(new PrimaryFundEntity(associationA, "Développement A", "Projet A", null));
        PrimaryFundEntity fundB = primaryFunds.save(new PrimaryFundEntity(associationB, "Solidarité B", "Projet B", null));
        LocalFundEntity localA = localFunds.save(new LocalFundEntity(fundA, "Caisse locale A"));
        LocalFundEntity localAOther = localFunds.save(new LocalFundEntity(fundA, "Caisse locale A2"));
        LocalFundEntity localB = localFunds.save(new LocalFundEntity(fundB, "Caisse locale B"));
        fundMemberships.save(new FundMembershipEntity(treasurer, fundA, localA, FundRole.TRESORIER));
        fundMemberships.save(new FundMembershipEntity(secondTreasurer, fundA, localAOther, FundRole.TRESORIER));

        MemberEntity memberA1 = members.save(new MemberEntity(associationA, "Awa", "Association A", null,
                manager, fundA, localA));
        MemberEntity memberA2 = members.save(new MemberEntity(associationA, "Mariam", "Association A", null,
                manager, fundA, localAOther));
        MemberEntity memberB = members.save(new MemberEntity(associationB, "Awa", "Association B", null,
                manager, fundB, localB));

        mockMvc.perform(secured(manager, associationA.getId(), get("/api/membres")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(secured(manager, associationB.getId(), get("/api/membres")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(memberB.getId().toString()));
        mockMvc.perform(secured(manager, associationA.getId(), get("/api/caisses-principales")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(fundA.getId().toString()));
        mockMvc.perform(secured(manager, associationA.getId(), get("/api/membres/{id}", memberB.getId())))
                .andExpect(status().isNotFound());

        mockMvc.perform(secured(treasurer, associationA.getId(), get("/api/membres")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(memberA1.getId().toString()));
        mockMvc.perform(secured(treasurer, associationA.getId(), get(
                        "/api/caisses-principales/{fundId}/caisses-locales/{localId}", fundA.getId(), localAOther.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(secured(secondTreasurer, associationA.getId(), get("/api/membres")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(memberA2.getId().toString()));
        mockMvc.perform(secured(secondTreasurer, associationA.getId(), get(
                        "/api/caisses-principales/{fundId}/caisses-locales/{localId}", fundA.getId(), localA.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(secured(treasurer, associationB.getId(), get("/api/membres")))
                .andExpect(status().isForbidden());
    }

    private AssociationEntity association(String name, UserAccountEntity manager) {
        AssociationEntity association = associations.save(new AssociationEntity(name, null, null, null, null));
        associationMemberships.save(new AssociationMembershipEntity(association, manager));
        return association;
    }

    private MockHttpServletRequestBuilder secured(UserAccountEntity user, UUID associationId,
                                                    MockHttpServletRequestBuilder request) {
        return request.header("X-Association-Id", associationId.toString()).with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user(new BeretaUserDetails(user)));
    }
}
