package com.bereta.admin;

import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Espace administrateur, lot 2 : associations (archivage en lecture seule, restauration, transfert du responsable)
 * et caisses locales (supervision, archivage, réaffectation du trésorier). L'administrateur n'agit plus comme
 * responsable dans les associations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAssociationsFundsIntegrationTest {

    private static final String PASSWORD = "Bereta2025";
    private static final String REASON = "Demande écrite du responsable, ticket 77";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void newAdminRoutesAreForbiddenToManagersAndTreasurers() throws Exception {
        String manager = token(account("RESPONSABLE"));
        String treasurer = token(account("TRESORIER"));
        UUID id = UUID.randomUUID();
        List<MockHttpServletRequestBuilder> routes = List.of(
                post("/api/admin/associations/" + id + "/restore"), post("/api/admin/associations/" + id + "/transfer"),
                get("/api/admin/funds"), get("/api/admin/funds/" + id), post("/api/admin/funds/" + id + "/archive"),
                post("/api/admin/funds/" + id + "/restore"), post("/api/admin/funds/" + id + "/reassign-treasurer"));
        for (String token : List.of(manager, treasurer)) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(route.header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON)
                        .content(reason())).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void anArchivedAssociationIsReadOnlyForItsUsersUntilRestored() throws Exception {
        UserAccountEntity managerAccount = account("RESPONSABLE");
        String manager = token(managerAccount);
        String admin = token(account("ADMIN"));
        String association = createAssociation(manager, "Lecture seule");
        String primary = primaryFund(manager, association);

        call(admin, get("/api/admin/associations/" + association), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibles[0].userId").value(managerAccount.getId().toString()))
                .andExpect(jsonPath("$.primaryFundId").value(primary))
                .andExpect(jsonPath("$.balances.totalCollected").value(0));
        call(admin, get("/api/admin/associations?q=Lecture seule&status=ACTIVE&sort=name&direction=asc"), null)
                .andExpect(jsonPath("$.items[0].responsibleName").value("Compte responsable"));
        // L'administrateur consulte depuis son espace : il n'a plus d'accès de responsable.
        call(admin, scoped(get("/api/caisses-principales"), association), null).andExpect(status().isForbidden());

        call(admin, post("/api/admin/associations/" + association + "/archive"), reason())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        call(manager, get("/api/associations"), null)
                .andExpect(jsonPath("$[?(@.id == '" + association + "')].status").value("ARCHIVED"));
        call(manager, scoped(get("/api/caisses-principales"), association), null).andExpect(status().isOk());
        call(manager, scoped(post("/api/caisses-principales/" + primary + "/caisses-locales"), association),
                "{\"name\":\"Refusée\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("archivée")));
        call(admin, get("/api/admin/associations?status=ARCHIVED&q=Lecture seule"), null)
                .andExpect(jsonPath("$.totalItems").value(1));

        call(admin, post("/api/admin/associations/" + association + "/restore"), reason())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        call(admin, post("/api/admin/associations/" + association + "/restore"), reason()).andExpect(status().isConflict());
        call(manager, scoped(post("/api/caisses-principales/" + primary + "/caisses-locales"), association),
                "{\"name\":\"Acceptée\"}").andExpect(status().isCreated());

        assertThat(auditCount(association, "ASSOCIATION_ARCHIVED")).isEqualTo(1);
        assertThat(auditCount(association, "ASSOCIATION_RESTORED")).isEqualTo(1);
        call(admin, post("/api/admin/associations/" + UUID.randomUUID() + "/restore"), reason())
                .andExpect(status().isNotFound());
    }

    @Test
    void transferGivesTheRightsToTheNewResponsibleAndRemovesThemFromThePreviousOne() throws Exception {
        UserAccountEntity first = account("RESPONSABLE");
        UserAccountEntity second = account("RESPONSABLE");
        UserAccountEntity coManager = account("RESPONSABLE");
        UserAccountEntity treasurerAccount = account("TRESORIER");
        UserAccountEntity disabled = account("RESPONSABLE");
        disabled.setEnabled(false);
        users.save(disabled);
        String manager = token(first);
        String admin = token(account("ADMIN"));
        String association = createAssociation(manager, "Transfert");
        String path = "/api/admin/associations/" + association + "/transfer";

        call(admin, post(path), transfer(second, first)).andExpect(status().isNotFound());
        call(admin, post(path), transfer(first, first)).andExpect(status().isConflict());
        call(admin, post(path), transfer(first, treasurerAccount))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Seul un compte responsable peut recevoir une association."));
        call(admin, post(path), transfer(first, disabled)).andExpect(status().isConflict());
        call(admin, post(path), "{\"fromUserId\":\"%s\",\"toUserId\":\"%s\"}".formatted(first.getId(), second.getId()))
                .andExpect(status().isBadRequest());
        call(manager, scoped(post("/api/associations/" + association + "/responsables"), association),
                "{\"email\":\"%s\"}".formatted(coManager.getEmail())).andExpect(status().isCreated());
        call(admin, post(path), transfer(first, coManager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ce compte est déjà responsable de cette association."));
        assertThat(auditCount(association, "ASSOCIATION_TRANSFERRED")).isZero();

        call(admin, post(path), transfer(first, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibles.length()").value(2));
        call(manager, scoped(get("/api/caisses-principales"), association), null).andExpect(status().isForbidden());
        call(token(second), scoped(get("/api/caisses-principales"), association), null).andExpect(status().isOk());
        // Retour vers l'ancien responsable : son rattachement existant est réactivé.
        call(admin, post(path), transfer(second, first)).andExpect(status().isOk());
        call(manager, scoped(get("/api/caisses-principales"), association), null).andExpect(status().isOk());

        assertThat(auditCount(association, "ASSOCIATION_TRANSFERRED")).isEqualTo(2);
        call(admin, get("/api/admin/audit-logs?associationId=" + association + "&action=ASSOCIATION_TRANSFERRED"), null)
                .andExpect(jsonPath("$.items[1].before.responsibleId").value(first.getId().toString()))
                .andExpect(jsonPath("$.items[1].after.responsibleId").value(second.getId().toString()))
                .andExpect(jsonPath("$.items[1].reason").value(REASON));
    }

    @Test
    void fundsAreSupervisedArchivedOnlyWhenEmptyAndTheirTreasurerCanBeReassigned() throws Exception {
        UserAccountEntity managerAccount = account("RESPONSABLE");
        String manager = token(managerAccount);
        String admin = token(account("ADMIN"));
        UserAccountEntity firstTreasurer = account("TRESORIER");
        UserAccountEntity secondTreasurer = account("TRESORIER");
        UserAccountEntity responsibleElsewhere = account("RESPONSABLE");
        String association = createAssociation(manager, "Caisses supervisées");
        String primary = primaryFund(manager, association);
        String kayes = localFund(manager, association, primary, "Caisse Kayes");
        String empty = localFund(manager, association, primary, "Caisse Vide");

        call(admin, get("/api/admin/funds?associationId=" + association + "&sort=name&direction=asc"), null)
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Caisse Kayes"))
                .andExpect(jsonPath("$.items[0].associationName").value("Caisses supervisées"));

        // Réaffectation : contrôles avant toute écriture.
        String reassign = "/api/admin/funds/" + kayes + "/reassign-treasurer";
        call(admin, post(reassign), reassign(managerAccount, null)).andExpect(status().isConflict());
        call(admin, post(reassign), reassign(account("ADMIN"), null)).andExpect(status().isConflict());
        call(admin, post(reassign), reassign(firstTreasurer, secondTreasurer)).andExpect(status().isNotFound());
        call(admin, post(reassign), reassign(firstTreasurer, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treasurers[0].userId").value(firstTreasurer.getId().toString()));
        call(admin, post(reassign), reassign(firstTreasurer, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ce compte est déjà trésorier de cette caisse."));
        call(admin, post(reassign), reassign(firstTreasurer, firstTreasurer)).andExpect(status().isConflict());

        // Le trésorier encaisse 20 € en espèces dans sa caisse.
        String treasurer = token(firstTreasurer);
        call(treasurer, scoped(post("/api/membres"), association),
                "{\"firstName\":\"Awa\",\"lastName\":\"Traoré\",\"localFundId\":\"%s\"}".formatted(kayes))
                .andExpect(status().isCreated());
        call(manager, scoped(post("/api/campagnes"), association),
                "{\"primaryFundId\":\"%s\",\"name\":\"Cotisation\",\"campaignType\":\"ANNUAL\",\"campaignYear\":2026,\"expectedAmount\":50}"
                        .formatted(primary)).andExpect(status().isCreated());
        String enrollment = json(call(treasurer, scoped(get("/api/paiements/inscriptions-a-payer"), association), null))
                .get(0).get("id").asText();
        call(treasurer, scoped(post("/api/paiements/inscriptions/" + enrollment), association),
                "{\"amount\":20,\"paidOn\":\"%s\",\"paymentMethod\":\"ESPECES\"}".formatted(LocalDate.now()))
                .andExpect(status().isCreated());
        call(admin, get("/api/admin/funds/" + kayes), null)
                .andExpect(jsonPath("$.balance.collected").value(20))
                .andExpect(jsonPath("$.balance.availableLocally").value(20))
                .andExpect(jsonPath("$.memberCount").value(1));

        // Archivage refusé tant que l'argent n'est pas remis puis confirmé.
        String archive = "/api/admin/funds/" + kayes + "/archive";
        call(admin, post(archive), reason())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(containsString("Il reste 20")));
        String remittance = json(call(treasurer, scoped(post("/api/caisses-locales/" + kayes + "/remises"), association),
                "{\"amount\":20,\"transferredOn\":\"%s\",\"method\":\"ESPECES\"}".formatted(LocalDate.now()))
                .andExpect(status().isCreated())).get("id").asText();
        call(admin, post(archive), reason())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(containsString("attend la confirmation")));
        call(manager, scoped(post("/api/caisses-principales/" + primary + "/remises/" + remittance + "/confirmer"),
                association), null).andExpect(status().isOk());
        call(admin, get("/api/admin/associations/" + association), null)
                .andExpect(jsonPath("$.balances.totalCollected").value(20))
                .andExpect(jsonPath("$.balances.central").value(20))
                .andExpect(jsonPath("$.balances.availableLocally").value(0))
                .andExpect(jsonPath("$.localFunds.length()").value(2))
                .andExpect(jsonPath("$.openCampaignCount").value(1));

        // Remplacement du trésorier : les encaissements passés gardent leur auteur et leur caisse.
        call(admin, post(reassign), reassign(secondTreasurer, firstTreasurer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treasurers.length()").value(1))
                .andExpect(jsonPath("$.treasurers[0].userId").value(secondTreasurer.getId().toString()));
        call(treasurer, scoped(get("/api/caisses-principales"), association), null).andExpect(status().isForbidden());
        assertThat(jdbc.queryForMap("select recorded_by_user_id, local_fund_id from payments where member_campaign_id = ?",
                UUID.fromString(enrollment)).values().stream().map(String::valueOf).toList())
                .containsExactly(firstTreasurer.getId().toString(), kayes);

        call(admin, post(archive), reason()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        call(admin, post(archive), reason()).andExpect(status().isConflict());
        String newTreasurer = token(secondTreasurer);
        call(newTreasurer, scoped(get("/api/caisses-principales/" + primary + "/caisses-locales/" + kayes), association), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        call(newTreasurer, scoped(post("/api/membres"), association),
                "{\"firstName\":\"Refus\",\"lastName\":\"Archive\",\"localFundId\":\"%s\"}".formatted(kayes))
                .andExpect(status().isConflict());
        call(admin, post(reassign), reassign(firstTreasurer, null)).andExpect(status().isConflict());
        // Une nouvelle campagne n'inscrit pas les membres d'une caisse archivée.
        call(manager, scoped(post("/api/campagnes"), association),
                "{\"primaryFundId\":\"%s\",\"name\":\"Fête\",\"campaignType\":\"EVENT\",\"expectedAmount\":10}"
                        .formatted(primary)).andExpect(jsonPath("$.enrolledCount").value(0));
        call(admin, get("/api/admin/funds?status=ARCHIVED&q=Caisses supervisées&sort=association"), null)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].treasurerNames[0]").value("Compte tresorier"));

        call(admin, post("/api/admin/funds/" + kayes + "/restore"), reason())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        call(admin, post("/api/admin/funds/" + kayes + "/restore"), reason()).andExpect(status().isConflict());

        // Un trésorier de l'association ne peut pas en devenir le responsable.
        call(admin, post("/api/admin/funds/" + empty + "/reassign-treasurer"), reassign(responsibleElsewhere, null))
                .andExpect(status().isOk());
        call(admin, post("/api/admin/associations/" + association + "/transfer"), transfer(managerAccount, responsibleElsewhere))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(containsString("trésorier")));

        assertThat(auditCount(kayes, "TREASURER_REASSIGNED")).isEqualTo(2);
        assertThat(auditCount(kayes, "LOCAL_FUND_ARCHIVED")).isEqualTo(1);
        assertThat(auditCount(kayes, "LOCAL_FUND_RESTORED")).isEqualTo(1);
        call(admin, get("/api/admin/funds/" + UUID.randomUUID()), null).andExpect(status().isNotFound());
    }

    // ---- Outils ------------------------------------------------------------------------------

    private String createAssociation(String manager, String name) throws Exception {
        return json(call(manager, post("/api/associations"), "{\"name\":\"%s\"}".formatted(name))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private String primaryFund(String manager, String association) throws Exception {
        return json(call(manager, scoped(get("/api/caisses-principales"), association), null)).get(0).get("id").asText();
    }

    private String localFund(String manager, String association, String primary, String name) throws Exception {
        return json(call(manager, scoped(post("/api/caisses-principales/" + primary + "/caisses-locales"), association),
                "{\"name\":\"%s\"}".formatted(name)).andExpect(status().isCreated())).get("id").asText();
    }

    private static MockHttpServletRequestBuilder scoped(MockHttpServletRequestBuilder request, String association) {
        return request.header("X-Association-Id", association);
    }

    private static String reason() {
        return "{\"reason\":\"%s\"}".formatted(REASON);
    }

    private static String transfer(UserAccountEntity from, UserAccountEntity to) {
        return "{\"fromUserId\":\"%s\",\"toUserId\":\"%s\",\"reason\":\"%s\"}".formatted(from.getId(), to.getId(), REASON);
    }

    private static String reassign(UserAccountEntity to, UserAccountEntity replaced) {
        return replaced == null
                ? "{\"toUserId\":\"%s\",\"reason\":\"%s\"}".formatted(to.getId(), REASON)
                : "{\"toUserId\":\"%s\",\"replaceUserId\":\"%s\",\"reason\":\"%s\"}".formatted(to.getId(), replaced.getId(), REASON);
    }

    private UserAccountEntity account(String role) {
        String phone = "+3376" + "%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000));
        return users.save(new UserAccountEntity("adm2-" + UUID.randomUUID() + "@example.test", phone,
                "Compte " + role.toLowerCase(), passwordEncoder.encode(PASSWORD), role));
    }

    private String token(UserAccountEntity user) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(user.getPhoneE164(), PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long auditCount(String targetId, String action) {
        return jdbc.queryForObject("select count(*) from admin_audit_log where target_id = ? and action = ?",
                Long.class, UUID.fromString(targetId), action);
    }
}
