package com.bereta.imports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MemberImportRowRepository extends JpaRepository<MemberImportRowEntity, UUID> {

    List<MemberImportRowEntity> findAllByImportIdOrderByLineNumberAsc(UUID importId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from MemberImportRowEntity r where r.importId = :importId")
    int deleteAllForImport(@Param("importId") UUID importId);
}
