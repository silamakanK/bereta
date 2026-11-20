package com.bereta.admin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Locale;

/** Pagination Criteria commune aux listes de l'espace administrateur (filtres, tri et page côté serveur). */
final class AdminCriteria {

    static final int MAX_PAGE_SIZE = 100;

    private AdminCriteria() {
    }

    @FunctionalInterface
    interface Where<T> {
        Predicate[] apply(CriteriaBuilder cb, Root<T> root);
    }

    @FunctionalInterface
    interface Sort<T> {
        Order apply(CriteriaBuilder cb, Root<T> root);
    }

    static <T> Page<T> page(EntityManager entityManager, Class<T> type, Where<T> where, Sort<T> sort,
                            int page, int size) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));

        CriteriaQuery<T> select = cb.createQuery(type);
        Root<T> root = select.from(type);
        select.where(where.apply(cb, root)).orderBy(sort.apply(cb, root));
        List<T> content = entityManager.createQuery(select)
                .setFirstResult((int) pageRequest.getOffset()).setMaxResults(pageRequest.getPageSize()).getResultList();

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<T> counted = count.from(type);
        count.select(cb.count(counted)).where(where.apply(cb, counted));
        return new PageImpl<>(content, pageRequest, entityManager.createQuery(count).getSingleResult());
    }

    /** Motif « contient » insensible à la casse, ou null si la recherche est vide. */
    static String contains(String query) {
        return query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
