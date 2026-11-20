package com.bereta.admin;

import com.bereta.auth.UserAccountEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Recherche des comptes côté serveur : texte (nom, téléphone, e-mail), rôle, statut, tri et pagination. */
@Component
class AdminUserQuery {

    static final int MAX_PAGE_SIZE = 100;

    @PersistenceContext
    private EntityManager entityManager;

    record Filter(String query, String role, Boolean enabled, String sort, boolean ascending) {
    }

    Page<UserAccountEntity> search(Filter filter, int page, int size) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));

        CriteriaQuery<UserAccountEntity> select = cb.createQuery(UserAccountEntity.class);
        Root<UserAccountEntity> user = select.from(UserAccountEntity.class);
        select.where(predicates(cb, user, filter)).orderBy(order(cb, user, filter));
        List<UserAccountEntity> content = entityManager.createQuery(select)
                .setFirstResult((int) pageRequest.getOffset()).setMaxResults(pageRequest.getPageSize()).getResultList();

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<UserAccountEntity> counted = count.from(UserAccountEntity.class);
        count.select(cb.count(counted)).where(predicates(cb, counted, filter));
        return new PageImpl<>(content, pageRequest, entityManager.createQuery(count).getSingleResult());
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<UserAccountEntity> user, Filter filter) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter.query() != null && !filter.query().isBlank()) {
            String like = "%" + filter.query().trim().toLowerCase(Locale.ROOT) + "%";
            String digits = "%" + filter.query().replaceAll("\\D", "") + "%";
            List<Predicate> text = new ArrayList<>(List.of(
                    cb.like(cb.lower(user.get("displayName")), like),
                    cb.like(cb.lower(user.get("email")), like)));
            if (digits.length() > 3) text.add(cb.like(user.get("phoneE164"), digits));
            predicates.add(cb.or(text.toArray(Predicate[]::new)));
        }
        if (filter.role() != null && !filter.role().isBlank()) predicates.add(cb.equal(user.get("role"), filter.role()));
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
