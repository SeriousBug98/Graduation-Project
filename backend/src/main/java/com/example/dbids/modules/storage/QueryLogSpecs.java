// src/main/java/com/example/dbids/modules/storage/QueryLogSpecs.java
package com.example.dbids.modules.storage;

import com.example.dbids.sqlite.model.QueryLog;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public final class QueryLogSpecs {
    private QueryLogSpecs() {}

    public static Specification<QueryLog> userEqualsIgnoreCase(String userId) {
        if (userId == null || userId.isBlank()) return null;
        final String u = userId.trim().toLowerCase();
        return (root, q, cb) -> cb.equal(cb.lower(root.get("userId")), u);
    }

    public static Specification<QueryLog> statusIn(Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) return null;
        return (root, q, cb) -> root.get("status").in(statuses);
    }

    /** sqlSummary keyword like (소문자 비교) */
    public static Specification<QueryLog> summaryLike(String keywords) {
        if (keywords == null || keywords.isBlank()) return null;
        final String pattern = "%" + keywords.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("sqlSummary")), pattern);
    }

    /** executedAt between (ISO-8601 문자열 전제) */
    public static Specification<QueryLog> executedBetween(String from, String to) {
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) return null;
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            return (root, q, cb) -> cb.between(root.get("executedAt"), from.trim(), to.trim());
        }
        if (from != null && !from.isBlank()) {
            return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("executedAt"), from.trim());
        }
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("executedAt"), to.trim());
    }

    /** returnRows >= min && <= max */
    public static Specification<QueryLog> rowsBetween(Integer min, Integer max) {
        if (min == null && max == null) return null;
        if (min != null && max != null) {
            return (root, q, cb) -> cb.between(root.get("returnRows"), min, max);
        }
        if (min != null) return (root, q, cb) -> cb.ge(root.get("returnRows"), min);
        return (root, q, cb) -> cb.le(root.get("returnRows"), max);
    }
}
