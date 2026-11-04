package com.bereta.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntity, UUID> {
    Page<AdminAuditEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
