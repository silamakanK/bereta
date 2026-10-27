package com.bereta.members;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<MemberEntity, UUID> {

    List<MemberEntity> findAllByOrderByLastNameAscFirstNameAsc();
}
