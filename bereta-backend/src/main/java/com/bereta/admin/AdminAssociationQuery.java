package com.bereta.admin;

import com.bereta.associations.AssociationEntity;
import com.bereta.associations.AssociationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Recherche des associations côté serveur : nom, statut, tri et pagination. */
@Component
class AdminAssociationQuery {

    @PersistenceContext
    private EntityManager entityManager;

    record Filter(String query, AssociationStatus status, String sort, boolean ascending) {
    }

    Page<AssociationEntity> search(Filter filter, int page, int size) {
        return AdminCriteria.page(entityManager, AssociationEntity.class,
                (cb, association) -> predicates(cb, association, filter),
                (cb, association) -> order(cb, association, filter), page, size);
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<AssociationEntity> association, Filter filter) {
        List<Predicate> predicates = new ArrayList<>();
        String like = AdminCriteria.contains(filter.query());
        if (like != null) predicates.add(cb.like(cb.lower(association.get("name")), like));
        if (filter.status() != null) predicates.add(cb.equal(association.get("status"), filter.status()));
        return predicates.toArray(Predicate[]::new);
    }

    private static Order order(CriteriaBuilder cb, Root<AssociationEntity> association, Filter filter) {
        String property = "name".equals(filter.sort()) ? "name" : "createdAt";
        return filter.ascending() ? cb.asc(association.get(property)) : cb.desc(association.get(property));
    }
}
