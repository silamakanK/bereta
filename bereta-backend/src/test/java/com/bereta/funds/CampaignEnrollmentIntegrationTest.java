package com.bereta.funds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inscriptions aux campagnes : inscription automatique à la création, membres de la caisse principale,
 * doublons, périmètre du trésorier, clôture, montant dû copié et caisse figée dans les paiements.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampaignEnrollmentIntegrationTest {

    private static final String PASSWORD = "Bereta2025";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void campaignCreationEnrollsActiveMembersAndPrimaryFundMembersCanEnrollAndPay() throws Exception {
        String manager = registerManager();
        String association = createAssociation(manager, "Sigida Nyetan");
        String primary = primaryFundOf(manager, association);
        String localA = createLocalFund(manager, association, primary, "Caisse 2");

        String fatou = createMember(manager, association, "Fatou", "Kamissoko", null);       // caisse principale
        String binta = createMember(manager, association, "Binta", "Diallo", localA);
        String archived = createMember(manager, association, "Ancien", "Membre", localA);
        call(manager, association, post("/api/membres/" + archived + "/archiver"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        // Création : les 2 membres actifs sont inscrits dans la même transaction, chacun dans sa caisse.
        JsonNode campaign = json(call(manager, association, post("/api/campagnes"),
                "{\"primaryFundId\":\"%s\",\"name\":\"Sira kura\",\"campaignType\":\"ANNUAL\",\"campaignYear\":2026,\"expectedAmount\":40}"
                        .formatted(primary)).andExpect(status().isCreated()));
        String campaignId = campaign.get("id").asText();
        assertThat(campaign.get("enrolledCount").asLong()).isEqualTo(2);
        assertThat(campaign.get("status").asText()).isEqualTo("OUVERTE");

        JsonNode tracking = json(call(manager, association, get("/api/membres/suivi"), null).andExpect(status().isOk()));
        JsonNode fatouEnrollment = row(tracking, fatou).get("enrollments").get(0);
        assertThat(fatouEnrollment.get("localFundId").isNull()).isTrue();
        assertThat(fatouEnrollment.get("localFundName").asText()).isEqualTo("Caisse principale");
        assertThat(fatouEnrollment.get("status").asText()).isEqualTo("A_PAYER");
        assertThat(row(tracking, binta).at("/enrollments/0/localFundId").asText()).isEqualTo(localA);
        assertThat(row(tracking, archived).get("enrollments")).isEmpty();

        // Nouveau membre pendant la campagne : PAS inscrit automatiquement ; inscription manuelle, puis doublon refusé.
        String newcomer = createMember(manager, association, "Awa", "Nouvelle", null);
        assertThat(row(json(call(manager, association, get("/api/membres/suivi"), null)), newcomer).get("enrollments")).isEmpty();
        call(manager, association, post("/api/membres/" + newcomer + "/inscriptions"),
                "{\"campaignId\":\"%s\"}".formatted(campaignId))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.localFundName").value("Caisse principale"));
        call(manager, association, post("/api/membres/" + newcomer + "/inscriptions"),
                "{\"campaignId\":\"%s\"}".formatted(campaignId)).andExpect(status().isConflict());

        // Paiement d'un membre de la caisse principale : compté directement dans le solde central.
        call(manager, association, post("/api/paiements/inscriptions/" + fatouEnrollment.get("id").asText()),
                "{\"amount\":40,\"paidOn\":\"2026-11-05\",\"paymentMethod\":\"ESPECES\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.localFundName").value("Caisse principale"));
        call(manager, association, get("/api/caisses-principales/" + primary + "/remises/synthese"), null)
                .andExpect(jsonPath("$.centralBalance").value(40.0))
                .andExpect(jsonPath("$.collectedCentrally").value(40.0));

        // Le montant dû est copié : modifier la campagne ne change pas les inscriptions existantes.
        jdbc.update("update campaigns set expected_amount = 99 where id = ?", UUID.fromString(campaignId));
        JsonNode after = json(call(manager, association, get("/api/membres/suivi"), null));
        assertThat(row(after, binta).at("/enrollments/0/expectedAmount").asDouble()).isEqualTo(40.0);
        assertThat(row(after, fatou).at("/enrollments/0/status").asText()).isEqualTo("PAYE");

        // Clôture : plus d'inscription.
        call(manager, association, post("/api/campagnes/" + campaignId + "/cloture"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOTUREE"));
        String late = createMember(manager, association, "Retard", "Membre", localA);
        call(manager, association, post("/api/membres/" + late + "/inscriptions"),
                "{\"campaignId\":\"%s\"}".formatted(campaignId)).andExpect(status().isConflict());
    }

    @Test
    void treasurerOnlyEnrollsMembersOfHisFundAndPaymentsKeepTheFundOfTheirCollection() throws Exception {
        String manager = registerManager();
        String association = createAssociation(manager, "Zawiya");
        String primary = primaryFundOf(manager, association);
        String localA = createLocalFund(manager, association, primary, "Caisse A");
        String localB = createLocalFund(manager, association, primary, "Caisse B");
        String treasurer = createTreasurer(manager, association, primary, localA);

        String campaignId = json(call(manager, association, post("/api/campagnes"),
                "{\"primaryFundId\":\"%s\",\"name\":\"Cotisation\",\"campaignType\":\"ANNUAL\",\"campaignYear\":2026,\"expectedAmount\":30}"
                        .formatted(primary)).andExpect(status().isCreated())).get("id").asText();
        String memberA = createMember(manager, association, "Moussa", "Caisse-A", localA);
        String memberB = createMember(manager, association, "Coumba", "Caisse-B", localB);
        String memberPrimary = createMember(manager, association, "Fatou", "Principale", null);

        // Trésorier : son membre, dans sa caisse uniquement.
        call(treasurer, association, post("/api/membres/" + memberA + "/inscriptions"),
                "{\"campaignId\":\"%s\",\"localFundId\":\"%s\"}".formatted(campaignId, localB)).andExpect(status().isForbidden());
        String enrollmentA = json(call(treasurer, association, post("/api/membres/" + memberA + "/inscriptions"),
                "{\"campaignId\":\"%s\"}".formatted(campaignId)).andExpect(status().isCreated())).get("id").asText();
        call(treasurer, association, post("/api/membres/" + memberB + "/inscriptions"),
                "{\"campaignId\":\"%s\"}".formatted(campaignId)).andExpect(status().isNotFound());
        call(treasurer, association, post("/api/membres/" + memberPrimary + "/inscriptions"),
                "{\"campaignId\":\"%s\"}".formatted(campaignId)).andExpect(status().isNotFound());
        call(treasurer, association, post("/api/campagnes/" + campaignId + "/cloture"), null).andExpect(status().isForbidden());
        call(treasurer, association, get("/api/membres/suivi"), null)
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(memberA));

        // La caisse du paiement est figée : réaffecter le membre ne modifie pas l'ancien paiement.
        String paymentId = json(call(treasurer, association, post("/api/paiements/inscriptions/" + enrollmentA),
                "{\"amount\":10,\"paidOn\":\"2026-11-05\",\"paymentMethod\":\"ESPECES\"}")
                .andExpect(status().isCreated())).get("id").asText();
        jdbc.update("update members set created_in_local_fund_id = ? where id = ?",
                UUID.fromString(localB), UUID.fromString(memberA));
        JsonNode history = json(call(manager, association, get("/api/paiements"), null).andExpect(status().isOk()));
        JsonNode payment = null;
        for (JsonNode item : history) if (item.get("id").asText().equals(paymentId)) payment = item;
        assertThat(payment).isNotNull();
        assertThat(payment.get("localFundId").asText()).isEqualTo(localA);
        assertThat(payment.get("localFundName").asText()).isEqualTo("Caisse A");
        assertThat(row(json(call(manager, association, get("/api/membres/suivi"), null)), memberA).get("fundName").asText())
                .isEqualTo("Caisse B");
    }

    // ---- Outils ------------------------------------------------------------------------------

    private String registerManager() throws Exception {
        return json(postJson("/api/auth/register", "{\"phone\":\"%s\",\"password\":\"%s\",\"acceptTerms\":true}"
                .formatted(uniquePhone(), PASSWORD)).andExpect(status().isCreated())).get("accessToken").asText();
    }

    private String createAssociation(String token, String name) throws Exception {
        return json(call(token, null, post("/api/associations"), "{\"name\":\"%s\"}".formatted(name))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private String primaryFundOf(String token, String associationId) throws Exception {
        return json(call(token, associationId, get("/api/caisses-principales"), null)).get(0).get("id").asText();
    }

    private String createLocalFund(String token, String associationId, String primary, String name) throws Exception {
        return json(call(token, associationId, post("/api/caisses-principales/" + primary + "/caisses-locales"),
                "{\"name\":\"%s\"}".formatted(name)).andExpect(status().isCreated())).get("id").asText();
    }

    private String createMember(String token, String associationId, String first, String last, String localFund)
            throws Exception {
        String fund = localFund == null ? "" : ",\"localFundId\":\"%s\"".formatted(localFund);
        return json(call(token, associationId, post("/api/membres"),
                "{\"firstName\":\"%s\",\"lastName\":\"%s\"%s}".formatted(first, last, fund))
                .andExpect(status().isCreated())).get("id").asText();
    }

    /** Trésorier désigné par le responsable, connecté après avoir changé son mot de passe provisoire. */
    private String createTreasurer(String manager, String associationId, String primary, String localFund) throws Exception {
        String phone = uniquePhone();
        String temporary = json(call(manager, associationId, post("/api/caisses-principales/" + primary + "/acces"),
                "{\"localFundId\":\"%s\",\"phone\":\"%s\",\"firstName\":\"Samba\",\"lastName\":\"Trésorier\"}"
                        .formatted(localFund, phone)).andExpect(status().isCreated())).get("temporaryPassword").asText();
        String provisional = json(postJson("/api/auth/login", "{\"identifier\":\"%s\",\"password\":\"%s\"}"
                .formatted(phone, temporary)).andExpect(status().isOk())).get("accessToken").asText();
        return json(mockMvc.perform(post("/api/auth/password/change").header("Authorization", "Bearer " + provisional)
                .contentType(APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"Tresorier2025\"}".formatted(temporary)))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private static JsonNode row(JsonNode tracking, String memberId) {
        for (JsonNode item : tracking) if (item.get("id").asText().equals(memberId)) return item;
        throw new AssertionError("Membre absent du suivi : " + memberId);
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
        return "+3377" + "%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000));
    }
}
