package com.bereta.admin;

import com.bereta.funds.LocalFundEntity;
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
import java.util.UUID;

/** Recherche des caisses locales de toute la plateforme : association, statut, nom (caisse ou association). */
@Component
class AdminFundQuery {

    @PersistenceContext
    private EntityManager entityManager;

    record Filter(String query, UUID associationId, String status, String sort, boolean ascending) {
    }

    Page<LocalFundEntity> search(Filter filter, int page, int size) {
        return AdminCriteria.page(entityManager, LocalFundEntity.class, (cb, fund) -> predicates(cb, fund, filter),
                (cb, fund) -> order(cb, fund, filter), page, size);
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<LocalFundEntity> fund, Filter filter) {
        List<Predicate> predicates = new ArrayList<>();
        String like = AdminCriteria.contains(filter.query());
        if (like != null) {
            predicates.add(cb.or(cb.like(cb.lower(fund.get("name")), like),
                    cb.like(cb.lower(fund.get("association").get("name")), like)));
        }
        if (filter.associationId() != null) {
            predicates.add(cb.equal(fund.get("association").get("id"), filter.associationId()));
        }
        if (AdminCriteria.present(filter.status())) predicates.add(cb.equal(fund.get("status"), filter.status()));
        return predicates.toArray(Predicate[]::new);
    }

    private static Order order(CriteriaBuilder cb, Root<LocalFundEntity> fund, Filter filter) {
        var path = switch (filter.sort() == null ? "" : filter.sort()) {
            case "name" -> fund.get("name");
            case "association" -> fund.get("association").get("name");
            default -> fund.get("createdAt");
        };
        return filter.ascending() ? cb.asc(path) : cb.desc(path);
    }
}
