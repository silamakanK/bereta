package com.bereta.imports;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Purge des brouillons : dans une transaction, chaque nuit et au démarrage, sans jamais faire échouer l'application. */
class ImportPurgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T03:30:00Z");

    private final MemberImportRepository imports = mock(MemberImportRepository.class);
    private final MemberImportRowRepository rows = mock(MemberImportRowRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ImportPurgeService service = new ImportPurgeService(imports, rows, Duration.ofDays(7),
            Clock.fixed(NOW, ZoneOffset.UTC), transactions);

    @Test
    void nightlyPurgeCancelsStaleDraftsInsideATransaction() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        MemberImportEntity draft = mock(MemberImportEntity.class);
        UUID id = UUID.randomUUID();
        when(draft.getId()).thenReturn(id);
        when(imports.findAllByStatusAndUpdatedAtBefore(ImportStatus.BROUILLON, NOW.minus(Duration.ofDays(7))))
                .thenReturn(List.of(draft));

        service.purgeNightly();

        verify(rows).deleteAllForImport(id);
        verify(draft).cancel();
        verify(imports).saveAll(List.of(draft));
        verify(transactions).commit(any());
    }

    @Test
    void aFailingPurgeIsLoggedAndNeverStopsTheApplication() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(imports.findAllByStatusAndUpdatedAtBefore(eq(ImportStatus.BROUILLON), any()))
                .thenThrow(new IllegalStateException("base indisponible"));

        assertThatCode(service::purgeAtStartup).doesNotThrowAnyException();
        assertThatCode(service::purgeNightly).doesNotThrowAnyException();
        verify(transactions, never()).commit(any());
    }

    @Test
    void returnsZeroWhenNothingIsStale() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(imports.findAllByStatusAndUpdatedAtBefore(eq(ImportStatus.BROUILLON), any())).thenReturn(List.of());

        assertThat(service.purgeStaleDrafts()).isZero();
    }
}
