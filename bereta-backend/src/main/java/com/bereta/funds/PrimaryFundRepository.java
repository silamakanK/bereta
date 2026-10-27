package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrimaryFundRepository extends JpaRepository<PrimaryFundEntity, UUID> {
    List<PrimaryFundEntity> findAllByOrderByNameAsc();
}
