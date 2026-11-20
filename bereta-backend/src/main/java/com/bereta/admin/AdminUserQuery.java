package com.bereta.admin;

import com.bereta.auth.UserAccountEntity;
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

/** Recherche des comptes côté serveur : texte (nom, téléphone, e-mail), rôle, statut, tri et pagination. */
@Component
class AdminUserQuery {

    @PersistenceContext
    private EntityManager entityManager;

    record Filter(String query, String role, Boolean enabled, String sort, boolean ascending) {
    }

    Page<UserAccountEntity> search(Filter filter, int page, int size) {
        return AdminCriteria.page(entityManager, UserAccountEntity.class, (cb, user) -> predicates(cb, user, filter),
                (cb, user) -> order(cb, user, filter), page, size);
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<UserAccountEntity> user, Filter filter) {
        List<Predicate> predicates = new ArrayList<>();
        String like = AdminCriteria.contains(filter.query());
        if (like != null) {
            String digits = "%" + filter.query().replaceAll("\\D", "") + "%";
            List<Predicate> text = new ArrayList<>(List.of(
                    cb.like(cb.lower(user.get("displayName")), like),
                    cb.like(cb.lower(user.get("email")), like)));
            if (digits.length() > 3) text.add(cb.like(user.get("phoneE164"), digits));
            predicates.add(cb.or(text.toArray(Predicate[]::new)));
        }
        if (AdminCriteria.present(filter.role())) predicates.add(cb.equal(user.get("role"), filter.role()));
        if (filter.enabled() != null) predicates.add(cb.equal(user.get("enabled"), filter.enabled()));
        return predicates.toArray(Predicate[]::new);
    }

    private static Order order(CriteriaBuilder cb, Root<UserAccountEntity> user, Filter filter) {
        String property = switch (filter.sort() == null ? "" : filter.sort()) {
            case "name" -> "displayName";
            case "role" -> "role";
            default -> "createdAt";
        };
        return filter.ascending() ? cb.asc(user.get(property)) : cb.desc(user.get(property));
    }
}
