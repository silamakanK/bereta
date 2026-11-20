package com.bereta.admin;

import com.bereta.admin.AdminDtos.AuditEntry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** Journal d'audit : consultation filtrée et export CSV (lecture seule, aucune route de modification). */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {

    private final AdminAuditReader reader;

    public AdminAuditController(AdminAuditReader reader) {
        this.reader = reader;
    }

    @GetMapping
    public AdminPageResponse<AuditEntry> list(@RequestParam(required = false) UUID actorId,
                                              @RequestParam(required = false) String action,
                                              @RequestParam(required = false) String targetType,
                                              @RequestParam(required = false) UUID targetId,
                                              @RequestParam(required = false) UUID associationId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "25") int size) {
        return reader.list(filter(actorId, action, targetType, targetId, associationId, from, to), page, size);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) UUID actorId,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String targetType,
                                         @RequestParam(required = false) UUID targetId,
                                         @RequestParam(required = false) UUID associationId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = reader.csv(filter(actorId, action, targetType, targetId, associationId, from, to));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("journal-administration.csv").build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Période en dates (jour inclus) : du début du jour « from » au lendemain du jour « to ». */
    private static AdminAuditQuery.Filter filter(UUID actorId, String action, String targetType, UUID targetId,
                                                 UUID associationId, LocalDate from, LocalDate to) {
        Instant start = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new AdminAuditQuery.Filter(actorId, action, targetType, targetId, associationId, start, end);
    }
}
