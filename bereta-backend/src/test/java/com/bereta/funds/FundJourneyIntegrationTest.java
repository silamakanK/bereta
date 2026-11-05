package com.bereta.funds;

import com.bereta.admin.AdminAuditRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parcours : association (caisse principale automatique) → caisse locale → trésorier → périmètre du trésorier. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FundJourneyIntegrationTest {

    private static final String PASSWORD = "Bereta2025";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AdminAuditRepository adminAudit;

    @Test
    void managerCreatesAssociationLocalFundAndTreasurerWhoOnlySeesHisFund() throws Exception {
        String manager = registerManager();
        String associationId = createAssociation(manager, "Développement de Niogoméra");
        String primaryFundId = primaryFundOf(manager, associationId);

        // Deux caisses locales, la première sans trésorier.
        JsonNode saintDenis = json(call(manager, associationId, post("/api/caisses-principales/" + primaryFundId + "/caisses-locales"),
                "{\"name\":\"Foyer Saint-Denis\",\"location\":\"12 rue du Foyer, Saint-Denis\"}")
                .andExpect(status().isCreated()));
        assertThat(saintDenis.get("location").asText()).isEqualTo("12 rue du Foyer, Saint-Denis");
        String montreuil = json(call(manager, associationId, post("/api/caisses-principales/" + primaryFundId + "/caisses-locales"),
                "{\"name\":\"Foyer Montreuil\"}").andExpect(status().isCreated())).get("id").asText();
        call(manager, associationId, get("/api/caisses-principales/" + primaryFundId + "/caisses-locales"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name == 'Foyer Saint-Denis')].treasurers.length()").value(0));

        // Désignation : recherche exacte (aucun compte), puis création avec mot de passe provisoire.
        String phone = uniquePhone();
        call(manager, associationId, get("/api/caisses-principales/" + primaryFundId + "/acces/compte?identifiant=" + phone), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.exists").value(false));
        JsonNode designation = json(call(manager, associationId, post("/api/caisses-principales/" + primaryFundId + "/acces"),
                "{\"localFundId\":\"%s\",\"phone\":\"%s\",\"firstName\":\"Mamadou\",\"lastName\":\"Sissoko\"}"
                        .formatted(saintDenis.get("id").asText(), phone))
                .andExpect(status().isCreated()));
        String temporaryPassword = designation.get("temporaryPassword").asText();
        assertThat(designation.get("accountCreated").asBoolean()).isTrue();
        call(manager, associationId, get("/api/caisses-principales/" + primaryFundId + "/acces/compte?identifiant=" + phone), null)
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.displayName").value("Mamadou Sissoko"));
        assertThat(adminAudit.findAll()).extracting(entry -> entry.getAction()).contains("TREASURER_ASSIGNED", "ASSOCIATION_CREATED");

        // Première connexion du trésorier : changement de mot de passe obligatoire.
        JsonNode firstLogin = json(postJson("/api/auth/login", "{\"identifier\":\"%s\",\"password\":\"%s\"}"
                .formatted(phone, temporaryPassword)).andExpect(status().isOk()));
        assertThat(firstLogin.at("/user/mustChangePassword").asBoolean()).isTrue();
        String provisional = firstLogin.get("accessToken").asText();
        mockMvc.perform(get("/api/associations").header("Authorization", "Bearer " + provisional))
                .andExpect(status().isForbidden());
        String treasurer = json(mockMvc.perform(post("/api/auth/password/change").header("Authorization", "Bearer " + provisional)
                .contentType(APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"Tresorier2025\"}".formatted(temporaryPassword)))
                .andExpect(status().isOk())).get("accessToken").asText();

        // Périmètre : son association, sa seule caisse ; aucune action de gestion.
        mockMvc.perform(get("/api/associations").header("Authorization", "Bearer " + treasurer))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currentUserResponsible").value(false));
        call(treasurer, associationId, get("/api/caisses-principales/" + primaryFundId + "/caisses-locales"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Foyer Saint-Denis"));
        call(treasurer, associationId, get("/api/caisses-principales/" + primaryFundId + "/caisses-locales/" + montreuil), null)
                .andExpect(status().isForbidden());
        call(treasurer, associationId, post("/api/caisses-principales/" + primaryFundId + "/caisses-locales"),
                "{\"name\":\"Caisse pirate\"}").andExpect(status().isForbidden());
        call(treasurer, associationId, get("/api/caisses-principales/" + primaryFundId + "/acces/compte?identifiant=" + phone), null)
                .andExpect(status().isForbidden());
        call(treasurer, null, post("/api/associations"), "{\"name\":\"Mon association\"}").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + treasurer))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerReplacesThenRemovesTheTreasurerAndOtherAssociationsStayOutOfReach() throws Exception {
        String manager = registerManager();
        String associationId = createAssociation(manager, "Solidarité Rapatriement Mali");
        String primaryFundId = primaryFundOf(manager, associationId);
        String localFund = json(call(manager, associationId, post("/api/caisses-principales/" + primaryFundId + "/caisses-locales"),
                "{\"name\":\"Caisse Paris Nord\"}").andExpect(status().isCreated())).get("id").asText();

        String firstPhone = uniquePhone();
        String firstMembership = json(call(manager, associationId, post("/api/caisses-principales/" + primaryFundId + "/acces"),
                "{\"localFundId\":\"%s\",\"phone\":\"%s\",\"firstName\":\"Samba\",\"lastName\":\"Diakité\"}"
                        .formatted(localFund, firstPhone)).andExpect(status().isCreated())).at("/membership/id").asText();

        // Remplacement par un compte existant retrouvé par son e-mail exact.
        String otherEmail = "existant-" + UUID.randomUUID() + "@example.test";
        postJson("/api/auth/register", "{\"phone\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"acceptTerms\":true}"
                .formatted(uniquePhone(), otherEmail, PASSWORD)).andExpect(status().isCreated());
        call(manager, associationId, get("/api/caisses-principales/" + primaryFundId + "/acces/compte?identifiant=" + otherEmail), null)
                .andExpect(jsonPath("$.exists").value(true));
        JsonNode replaced = json(call(manager, associationId, post("/api/caisses-principales/" + primaryFundId + "/acces"),
                "{\"localFundId\":\"%s\",\"email\":\"%s\",\"replaceMembershipId\":\"%s\"}"
                        .formatted(localFund, otherEmail, firstMembership)).andExpect(status().isCreated()));
        assertThat(replaced.get("accountCreated").asBoolean()).isFalse();
        assertThat(replaced.get("temporaryPassword").isNull()).isTrue();
        call(manager, associationId, get("/api/caisses-principales/" + primaryFundId + "/caisses-locales/" + localFund), null)
                .andExpect(jsonPath("$.treasurers.length()").value(1))
                .andExpect(jsonPath("$.treasurers[0].email").value(otherEmail));

        // Retrait : la caisse redevient « Sans responsable » et l'ancien trésorier perd l'accès.
        String treasurer = json(postJson("/api/auth/login", "{\"identifier\":\"%s\",\"password\":\"%s\"}"
                .formatted(otherEmail, PASSWORD)).andExpect(status().isOk())).get("accessToken").asText();
        call(treasurer, associationId, get("/api/caisses-principales/" + primaryFundId + "/caisses-locales/" + localFund), null)
                .andExpect(status().isOk());
        call(manager, associationId, delete("/api/caisses-principales/" + primaryFundId + "/acces/" + replaced.at("/membership/id").asText()), null)
                .andExpect(status().isNoContent());
        call(manager, associationId, get("/api/caisses-principales/" + primaryFundId + "/caisses-locales/" + localFund), null)
                .andExpect(jsonPath("$.treasurers.length()").value(0));
        call(treasurer, associationId, get("/api/caisses-principales/" + primaryFundId + "/caisses-locales/" + localFund), null)
                .andExpect(status().isForbidden());
        assertThat(adminAudit.findAll()).extracting(entry -> entry.getAction()).contains("TREASURER_REMOVED");

        // Un autre responsable ne voit rien de cette association.
        String outsider = registerManager();
        call(outsider, associationId, get("/api/caisses-principales/" + primaryFundId + "/caisses-locales"), null)
                .andExpect(status().isForbidden());
    }

    // ---- Outils ------------------------------------------------------------------------------

    private String registerManager() throws Exception {
        return json(postJson("/api/auth/register", "{\"phone\":\"%s\",\"password\":\"%s\",\"acceptTerms\":true}"
                .formatted(uniquePhone(), PASSWORD)).andExpect(status().isCreated())).get("accessToken").asText();
    }

    private String createAssociation(String token, String name) throws Exception {
        return json(call(token, null, post("/api/associations"), "{\"name\":\"%s\",\"objective\":\"Entraide\"}".formatted(name))
                .andExpect(status().isCreated())).get("id").asText();
    }

    /** La caisse principale est créée automatiquement avec l'association. */
    private String primaryFundOf(String token, String associationId) throws Exception {
        JsonNode funds = json(call(token, associationId, get("/api/caisses-principales"), null).andExpect(status().isOk()));
        assertThat(funds).hasSize(1);
        assertThat(funds.get(0).get("name").asText()).isEqualTo("Caisse principale");
        return funds.get(0).get("id").asText();
    }

    private ResultActions call(String token, String associationId, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (associationId != null) request.header("X-Association-Id", associationId);
        if (body != null) request.contentType(APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static String uniquePhone() {
        return "+3378" + "%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000));
    }
}
