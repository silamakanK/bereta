package com.bereta.imports;

import com.bereta.auth.PhoneNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import db.migration.V26__backfill_member_phone_e164;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Import de membres par fichier : aperçu, doublons, corrections, validation transactionnelle et idempotente,
 * périmètre du trésorier, alerte « déjà importé », annulation, purge des brouillons et remplissage de phone_e164.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberImportIntegrationTest {

    private static final String PASSWORD = "Bereta2025";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ImportPurgeService purgeService;
    @Autowired private DataSource dataSource;

    @Test
    void analysesCorrectsAndValidatesACsvIntoThePrimaryFund() throws Exception {
        String manager = registerManager();
        String association = createAssociation(manager, "Import Sigida");
        String primary = primaryFundOf(manager, association);
        String existing = createMember(manager, association, "Fatou", "Kamissoko", "06 12 34 56 78", null);
        assertThat(jdbc.queryForObject("select phone_e164 from members where id = ?", String.class, UUID.fromString(existing)))
                .isEqualTo("+33612345678");

        // 1. Analyse : rien n'est encore créé.
        long membersBefore = countMembers(association);
        JsonNode preview = json(analyse(manager, association, null, "membres-point-virgule-utf8.csv")
                .andExpect(status().isCreated()));
        String importId = preview.get("id").asText();
        JsonNode rows = preview.get("rows");
        assertThat(preview.get("status").asText()).isEqualTo("BROUILLON");
        assertThat(preview.get("sourceType").asText()).isEqualTo("CSV");
        assertThat(preview.at("/fund/name").asText()).isEqualTo("Caisse principale");
        assertThat(rows).hasSize(6);
        assertThat(statuses(rows)).containsExactly("DOUBLON", "PRETE", "A_VERIFIER", "A_VERIFIER", "A_VERIFIER", "DOUBLON");
        assertThat(rows.get(0).at("/duplicateMember/id").asText()).isEqualTo(existing);
        assertThat(rows.get(0).get("duplicateAction").asText()).isEqualTo("IGNORER");
        assertThat(rows.get(1).get("firstName").asText()).isEqualTo("Mamadou");
        assertThat(rows.get(1).get("lastName").asText()).isEqualTo("N'Diaye");
        assertThat(rows.get(1).get("readLastName").asText()).isEqualTo("n'diaye");
        assertThat(rows.get(1).get("phoneE164").asText()).isEqualTo("+221771234567");
        assertThat(rows.get(2).get("issues").toString()).contains("Indicatif déduit (+223)");
        assertThat(rows.get(3).get("issues").toString()).contains("Téléphone invalide");
        assertThat(rows.get(4).get("issues").toString()).contains("Prénom manquant");
        assertThat(preview.at("/counters/toCreate").asInt()).isEqualTo(1);
        assertThat(preview.get("alreadyImportedAt").isNull()).isTrue();
        assertThat(countMembers(association)).isEqualTo(membersBefore);

        // 2. Corrections : confirmer, corriger, exclure, ajouter une ligne oubliée.
        ArrayNode changes = objectMapper.createArrayNode();
        for (JsonNode row : rows) {
            ObjectNode change = changes.addObject();
            change.put("id", row.get("id").asText());
            change.put("firstName", row.get("firstName").asText(null));
            change.put("lastName", row.get("lastName").asText(null));
            change.put("phone", row.get("phone").asText(null));
            change.put("included", row.get("lineNumber").asInt() != 5);
            change.put("confirmed", row.get("lineNumber").asInt() == 3);
            change.put("duplicateAction", row.get("duplicateAction").asText(null));
        }
        ((ObjectNode) changes.get(3)).put("phone", "+223 66 12 34 56");
        changes.addObject().put("firstName", "kadiatou").put("lastName", "sow").put("included", true).put("confirmed", true);
        JsonNode corrected = json(call(manager, association, put("/api/imports/" + importId + "/lignes"),
                objectMapper.writeValueAsString(objectMapper.createObjectNode().set("rows", changes)))
                .andExpect(status().isOk()));
        assertThat(corrected.at("/rows/6/lineNumber").asInt()).isEqualTo(7);
        assertThat(corrected.at("/rows/6/firstName").asText()).isEqualTo("Kadiatou");
        assertThat(corrected.at("/counters/toCreate").asInt()).isEqualTo(4);
        assertThat(corrected.at("/counters/excluded").asInt()).isEqualTo(1);
        assertThat(corrected.at("/counters/ignored").asInt()).isEqualTo(3);

        // 3. Inscription demandée sans campagne ouverte : refus, rien n'est créé.
        call(manager, association, post("/api/imports/" + importId + "/valider"), "{\"enrollInCurrentCampaign\":true}")
                .andExpect(status().isConflict());
        assertThat(countMembers(association)).isEqualTo(membersBefore);

        // 4. Validation avec inscription à la campagne en cours.
        String campaign = json(call(manager, association, post("/api/campagnes"),
                "{\"primaryFundId\":\"%s\",\"name\":\"Cotisation 2026\",\"campaignType\":\"ANNUAL\",\"campaignYear\":2026,\"expectedAmount\":25}"
                        .formatted(primary)).andExpect(status().isCreated())).get("id").asText();
        call(manager, association, post("/api/imports/" + importId + "/valider"), "{\"enrollInCurrentCampaign\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(4))
                .andExpect(jsonPath("$.ignored").value(3))
                .andExpect(jsonPath("$.enrolled").value(4))
                .andExpect(jsonPath("$.campaignName").value("Cotisation 2026"));
        UUID id = UUID.fromString(importId);
        assertThat(jdbc.queryForObject("select count(*) from members where import_id = ?", Long.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from member_campaigns mc join members m on m.id = mc.member_id "
                + "where m.import_id = ? and mc.campaign_id = ? and mc.local_fund_id is null and mc.expected_amount = 25",
                Long.class, id, UUID.fromString(campaign))).isEqualTo(4);
        assertThat(jdbc.queryForObject("select phone_e164 from members where import_id = ? and first_name = 'Mamadou'",
                String.class, id)).isEqualTo("+221771234567");
        assertThat(jdbc.queryForObject("select phone from members where import_id = ? and first_name = 'Mamadou'",
                String.class, id)).isEqualTo("+221 77 123 45 67");
        assertThat(jdbc.queryForObject("select count(*) from member_import_rows where import_id = ?", Long.class, id)).isZero();
        call(manager, association, get("/api/membres"), null)
                .andExpect(jsonPath("$[?(@.firstName == 'Kadiatou')].importId").value(importId))
                .andExpect(jsonPath("$[?(@.firstName == 'Kadiatou')].fundName").value("Caisse principale"));

        // 5. Deuxième appel à /valider : 409, aucun membre en plus.
        call(manager, association, post("/api/imports/" + importId + "/valider"), "{}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Cet import a déjà été validé."));
        assertThat(countMembers(association)).isEqualTo(membersBefore + 4);

        // 6. Même document renvoyé : alerte « déjà importé » ; le brouillon peut être annulé.
        JsonNode again = json(analyse(manager, association, null, "membres-point-virgule-utf8.csv").andExpect(status().isCreated()));
        assertThat(again.get("alreadyImportedAt").isNull()).isFalse();
        assertThat(statuses(again.get("rows")).get(1)).isEqualTo("DOUBLON"); // Mamadou existe désormais
        call(manager, association, delete("/api/imports/" + again.get("id").asText()), null).andExpect(status().isNoContent());
        call(manager, association, delete("/api/imports/" + again.get("id").asText()), null).andExpect(status().isConflict());
        call(manager, association, post("/api/imports/" + again.get("id").asText() + "/valider"), "{}").andExpect(status().isConflict());
        call(manager, association, get("/api/imports"), null)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("ANNULE"))
                .andExpect(jsonPath("$[1].status").value("VALIDE"))
                .andExpect(jsonPath("$[1].createdCount").value(4));
    }

    @Test
    void validatesOnlyOnceWhenTwoRequestsArriveTogether() throws Exception {
        String manager = registerManager();
        String association = createAssociation(manager, "Import concurrent");
        String primary = primaryFundOf(manager, association);
        String local = createLocalFund(manager, association, primary, "Caisse Kayes");
        JsonNode preview = json(analyse(manager, association, local, "membres.xlsx").andExpect(status().isCreated()));
        assertThat(preview.get("sourceType").asText()).isEqualTo("EXCEL");
        assertThat(preview.at("/counters/toCreate").asInt()).isEqualTo(2);
        String importId = preview.get("id").asText();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return call(manager, association, post("/api/imports/" + importId + "/valider"), "{}")
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) statuses.add(result.get());
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from members where import_id = ? and created_in_local_fund_id = ?",
                Long.class, UUID.fromString(importId), UUID.fromString(local))).isEqualTo(2);
    }

    @Test
    void treasurerImportsOnlyIntoHisOwnFund() throws Exception {
        String manager = registerManager();
        String association = createAssociation(manager, "Import trésorier");
        String primary = primaryFundOf(manager, association);
        String localA = createLocalFund(manager, association, primary, "Caisse A");
        String localB = createLocalFund(manager, association, primary, "Caisse B");
        String treasurer = createTreasurer(manager, association, primary, localA);
        createMember(manager, association, "Binta", "Sangaré", null, localB);

        analyse(treasurer, association, localB, "membres.xlsx").andExpect(status().isForbidden());
        analyse(treasurer, association, null, "membres.xlsx").andExpect(status().isForbidden());
        JsonNode own = json(analyse(treasurer, association, localA, "membres.xlsx").andExpect(status().isCreated()));
        // Doublon avec un membre d'une autre caisse : signalé sans révéler son nom.
        JsonNode binta = own.get("rows").get(0);
        assertThat(binta.get("status").asText()).isEqualTo("DOUBLON");
        assertThat(binta.at("/duplicateMember/visible").asBoolean()).isFalse();
        assertThat(binta.at("/duplicateMember/firstName").isNull()).isTrue();

        String managerImport = json(analyse(manager, association, localB, "membres.xlsx").andExpect(status().isCreated()))
                .get("id").asText();
        call(treasurer, association, get("/api/imports/" + managerImport), null).andExpect(status().isNotFound());
        call(treasurer, association, post("/api/imports/" + managerImport + "/valider"), "{}").andExpect(status().isNotFound());
        call(treasurer, association, get("/api/imports"), null)
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(own.get("id").asText()));
        call(treasurer, association, post("/api/imports/" + own.get("id").asText() + "/valider"), "{}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.ignored").value(1));
    }

    @Test
    void rejectsUnsupportedDocumentsWithAClearMessage() throws Exception {
        String manager = registerManager();
        String association = createAssociation(manager, "Import refusé");
        call(manager, association, multipart("/api/imports/membres/analyse")
                .file(new MockMultipartFile("fichiers", "liste.csv", "text/csv", "%PDF-1.4 faux csv".getBytes())), null)
                .andExpect(status().isUnsupportedMediaType());
        call(manager, association, multipart("/api/imports/membres/analyse"), null).andExpect(status().isBadRequest());
        call(manager, association, multipart("/api/imports/membres/analyse")
                .file(new MockMultipartFile("fichiers", "vide.csv", "text/csv", "prenom;nom\n".getBytes())), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Aucun membre trouvé dans ce document."));
    }

    @Test
    void purgesDraftsOlderThanSevenDays() throws Exception {
        String manager = registerManager();
        String association = createAssociation(manager, "Import purge");
        String stale = json(analyse(manager, association, null, "membres.xlsx")).get("id").asText();
        String fresh = json(analyse(manager, association, null, "membres-nom-complet.csv")).get("id").asText();
        jdbc.update("update member_imports set updated_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS)), UUID.fromString(stale));

        assertThat(purgeService.purgeStaleDrafts()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select status from member_imports where id = ?", String.class, UUID.fromString(stale)))
                .isEqualTo("ANNULE");
        assertThat(jdbc.queryForObject("select count(*) from member_import_rows where import_id = ?", Long.class,
                UUID.fromString(stale))).isZero();
        assertThat(jdbc.queryForObject("select status from member_imports where id = ?", String.class, UUID.fromString(fresh)))
                .isEqualTo("BROUILLON");
        assertThat(jdbc.queryForObject("select count(*) from member_import_rows where import_id = ?", Long.class,
                UUID.fromString(fresh))).isEqualTo(2);
    }

    @Test
    void backfillMigrationFillsPhoneE164WithoutTouchingPhone() throws Exception {
        String manager = registerManager();
        String association = createAssociation(manager, "Import migration");
        String readable = createMember(manager, association, "Awa", "Lisible", "00223 76 12 34 56", null);
        String unreadable = createMember(manager, association, "Sidi", "Illisible", "pas de numéro", null);
        jdbc.update("update members set phone_e164 = null where id in (?, ?)", UUID.fromString(readable), UUID.fromString(unreadable));

        try (Connection connection = dataSource.getConnection()) {
            assertThat(V26__backfill_member_phone_e164.backfill(connection, new PhoneNumberService("FR")))
                    .isGreaterThanOrEqualTo(1);
        }
        assertThat(jdbc.queryForMap("select phone, phone_e164 from members where id = ?", UUID.fromString(readable)))
                .containsEntry("PHONE", "00223 76 12 34 56").containsEntry("PHONE_E164", "+22376123456");
        assertThat(jdbc.queryForObject("select phone_e164 from members where id = ?", String.class, UUID.fromString(unreadable)))
                .isNull();
    }

    // ---- Outils ------------------------------------------------------------------------------

    private ResultActions analyse(String token, String associationId, String localFund, String sample) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/imports/membres/analyse")
                .file(new MockMultipartFile("fichiers", sample, "application/octet-stream",
                        MemberExtractionTest.resource(sample)));
        if (localFund != null) request.param("localFundId", localFund);
        return call(token, associationId, request, null);
    }

    private static List<String> statuses(JsonNode rows) {
        List<String> statuses = new ArrayList<>();
        rows.forEach(row -> statuses.add(row.get("status").asText()));
        return statuses;
    }

    private long countMembers(String associationId) {
        return jdbc.queryForObject("select count(*) from members where association_id = ?", Long.class,
                UUID.fromString(associationId));
    }

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

    private String createMember(String token, String associationId, String first, String last, String phone,
                                String localFund) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("firstName", first).put("lastName", last);
        if (phone != null) body.put("phone", phone);
        if (localFund != null) body.put("localFundId", localFund);
        return json(call(token, associationId, post("/api/membres"), body.toString())
                .andExpect(status().isCreated())).get("id").asText();
    }

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

    private ResultActions call(String token, String associationId, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (associationId != null) request.header("X-Association-Id", associationId);
        if (body != null) request.contentType(APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String uniquePhone() {
        return "+3361" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
    }
}
