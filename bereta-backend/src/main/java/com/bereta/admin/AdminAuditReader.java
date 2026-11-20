package com.bereta.admin;

import com.bereta.admin.AdminDtos.AuditEntry;
import com.bereta.auth.UserAccountEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Lecture du journal : réponses JSON et export CSV (séparateur « ; », protégé contre l'injection de formules). */
@Component
@Transactional(readOnly = true)
class AdminAuditReader {

    static final String INTERNAL_PROCEDURE = "Procédure interne";
    private static final String CSV_HEADER = "Date;Administrateur;Action;Type de cible;Cible;Association;Motif;Avant;Après;Adresse IP";

    private final AdminAuditQuery query;

    AdminAuditReader(AdminAuditQuery query) {
        this.query = query;
    }

    AdminPageResponse<AuditEntry> list(AdminAuditQuery.Filter filter, int page, int size) {
        return AdminPageResponse.from(query.search(filter, page, size), AdminAuditReader::entry);
    }

    String csv(AdminAuditQuery.Filter filter) {
        List<AdminAuditEntity> entries = query.export(filter);
        StringBuilder csv = new StringBuilder("﻿").append(CSV_HEADER).append("\r\n");
        for (AdminAuditEntity entry : entries) {
            AuditEntry row = entry(entry);
            csv.append(String.join(";", cell(row.createdAt().toString()), cell(row.actorName()), cell(row.action()),
                    cell(row.targetType()), cell(row.targetId() == null ? "" : row.targetId().toString()),
                    cell(row.associationName()), cell(row.reason() != null ? row.reason() : row.details()),
                    cell(values(row.before())), cell(values(row.after())), cell(row.ipAddress()))).append("\r\n");
        }
        return csv.toString();
    }

    static AuditEntry entry(AdminAuditEntity entry) {
        UserAccountEntity actor = entry.getActor();
        var association = entry.getAssociation();
        return new AuditEntry(entry.getId(), actor == null ? null : actor.getId(), actorName(actor), entry.getAction(),
                entry.getTargetType(), entry.getTargetId(), association == null ? null : association.getId(),
                association == null ? null : association.getName(), entry.getReason(), entry.getDetails(),
                entry.getBeforeValues(), entry.getAfterValues(), entry.getIpAddress(), entry.getCreatedAt());
    }

    private static String actorName(UserAccountEntity actor) {
        if (actor == null) return INTERNAL_PROCEDURE;
        if (actor.getDisplayName() != null) return actor.getDisplayName();
        return actor.getEmail() != null ? actor.getEmail() : actor.getPhoneE164();
    }

    private static String values(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return "";
        return values.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
    }

    /** Guillemets doublés ; une cellule commençant par = + - @ est préfixée d'une apostrophe (formule neutralisée). */
    static String cell(String value) {
        if (value == null) return "";
        String safe = value.isEmpty() || "=+-@\t\r".indexOf(value.charAt(0)) < 0 ? value : "'" + value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
