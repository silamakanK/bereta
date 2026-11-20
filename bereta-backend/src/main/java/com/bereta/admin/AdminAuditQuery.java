package com.bereta.admin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Recherche dans le journal (lecture seule) : auteur, action, cible, association, période ; du plus récent au plus ancien. */
@Component
class AdminAuditQuery {

    static final int MAX_EXPORT_ROWS = 10_000;

    @PersistenceContext
    private EntityManager entityManager;

    record Filter(UUID actorId, String action, String targetType, UUID targetId, UUID associationId,
                  Instant from, Instant to) {
    }

    Page<AdminAuditEntity> search(Filter filter, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, AdminUserQuery.MAX_PAGE_SIZE));
        List<AdminAuditEntity> content = list(filter, (int) pageRequest.getOffset(), pageRequest.getPageSize());
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<AdminAuditEntity> counted = count.from(AdminAuditEntity.class);
        count.select(cb.count(counted)).where(predicates(cb, counted, filter));
        return new PageImpl<>(content, pageRequest, entityManager.createQuery(count).getSingleResult());
    }

    List<AdminAuditEntity> export(Filter filter) {
        return list(filter, 0, MAX_EXPORT_ROWS);
    }

    private List<AdminAuditEntity> list(Filter filter, int offset, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AdminAuditEntity> select = cb.createQuery(AdminAuditEntity.class);
        Root<AdminAuditEntity> entry = select.from(AdminAuditEntity.class);
        select.where(predicates(cb, entry, filter)).orderBy(cb.desc(entry.get("createdAt")));
        return entityManager.createQuery(select).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<AdminAuditEntity> entry, Filter filter) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter.actorId() != null) predicates.add(cb.equal(entry.get("actor").get("id"), filter.actorId()));
        if (filter.action() != null && !filter.action().isBlank()) predicates.add(cb.equal(entry.get("action"), filter.action()));
        if (filter.targetType() != null && !filter.targetType().isBlank()) {
            predicates.add(cb.equal(entry.get("targetType"), filter.targetType()));
        }
        if (filter.targetId() != null) predicates.add(cb.equal(entry.get("targetId"), filter.targetId()));
        if (filter.associationId() != null) {
            predicates.add(cb.equal(entry.get("association").get("id"), filter.associationId()));
        }
        if (filter.from() != null) predicates.add(cb.greaterThanOrEqualTo(entry.get("createdAt"), filter.from()));
        if (filter.to() != null) predicates.add(cb.lessThan(entry.get("createdAt"), filter.to()));
        return predicates.toArray(Predicate[]::new);
    }
}
