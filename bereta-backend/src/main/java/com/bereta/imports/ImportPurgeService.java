package com.bereta.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Purge des brouillons abandonnés (données personnelles) : annulés et vidés de leurs lignes.
 * Lancée au démarrage (le serveur gratuit s'endort : une tâche planifiée seule pourrait ne jamais tourner)
 * puis chaque nuit.
 */
@Service
public class ImportPurgeService {

    private static final Logger log = LoggerFactory.getLogger(ImportPurgeService.class);

    private final MemberImportRepository imports;
    private final MemberImportRowRepository rows;
    private final Duration retention;
    private final Clock clock;

    public ImportPurgeService(MemberImportRepository imports, MemberImportRowRepository rows,
                              @Value("${bereta.imports.draft-retention:P7D}") Duration retention, Clock clock) {
        this.imports = imports;
        this.rows = rows;
        this.retention = retention;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void purgeAtStartup() {
        purgeStaleDrafts();
    }

    @Scheduled(cron = "${bereta.imports.purge-cron:0 30 3 * * *}")
    public void purgeNightly() {
        purgeStaleDrafts();
    }

    /** Renvoie le nombre de brouillons purgés. */
    @Transactional
    public int purgeStaleDrafts() {
        Instant limit = clock.instant().minus(retention);
        List<MemberImportEntity> stale = imports.findAllByStatusAndUpdatedAtBefore(ImportStatus.BROUILLON, limit);
        for (MemberImportEntity draft : stale) {
            rows.deleteAllForImport(draft.getId());
            draft.cancel();
        }
        imports.saveAll(stale);
        if (!stale.isEmpty()) log.info("Imports : {} brouillon(s) de plus de {} purgé(s).", stale.size(), retention);
        return stale.size();
    }
}
