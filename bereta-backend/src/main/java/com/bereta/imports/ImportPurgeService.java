package com.bereta.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Purge des brouillons abandonnés (données personnelles) : annulés et vidés de leurs lignes.
 * Lancée au démarrage (le serveur gratuit s'endort : une tâche planifiée seule pourrait ne jamais tourner)
 * puis chaque nuit. Une purge en échec est journalisée : elle ne doit jamais empêcher l'application de démarrer.
 */
@Service
public class ImportPurgeService {

    private static final Logger log = LoggerFactory.getLogger(ImportPurgeService.class);

    private final MemberImportRepository imports;
    private final MemberImportRowRepository rows;
    private final Duration retention;
    private final Clock clock;
    // Transaction explicite : un appel interne à une méthode @Transactional ne passe pas par le proxy Spring.
    private final TransactionTemplate transaction;

    public ImportPurgeService(MemberImportRepository imports, MemberImportRowRepository rows,
                              @Value("${bereta.imports.draft-retention:P7D}") Duration retention, Clock clock,
                              PlatformTransactionManager transactionManager) {
        this.imports = imports;
        this.rows = rows;
        this.retention = retention;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void purgeAtStartup() {
        purgeSafely();
    }

    @Scheduled(cron = "${bereta.imports.purge-cron:0 30 3 * * *}")
    public void purgeNightly() {
        purgeSafely();
    }

    private void purgeSafely() {
        try {
            purgeStaleDrafts();
        } catch (RuntimeException failure) {
            log.error("Imports : purge des brouillons impossible, nouvel essai à la prochaine exécution.", failure);
        }
    }

    /** Renvoie le nombre de brouillons purgés. */
    public int purgeStaleDrafts() {
        Integer purged = transaction.execute(status -> purgeInTransaction());
        return purged == null ? 0 : purged;
    }

    private int purgeInTransaction() {
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
