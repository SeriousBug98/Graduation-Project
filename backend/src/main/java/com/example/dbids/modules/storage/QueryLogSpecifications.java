package com.example.dbids.modules.storage;

import com.example.dbids.dto.QueryLogFilter;
import com.example.dbids.sqlite.model.QueryLog;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public final class QueryLogSpecifications {

    private QueryLogSpecifications() {}

    public static Specification<QueryLog> withFilter(QueryLogFilter f) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();

            if (f.user() != null && !f.user().isBlank()) {
                preds.add(cb.equal(root.get("userId"), f.user()));
            }
            if (f.keywords() != null && !f.keywords().isBlank()) {
                preds.add(cb.like(cb.lower(root.get("sqlSummary")), "%" + f.keywords().toLowerCase() + "%"));
            }
            if (f.status() != null && !f.status().isEmpty()) {
                preds.add(root.get("status").in(f.status()));
            }
            if (f.rowsMin() != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("returnRows"), f.rowsMin()));
            }
            if (f.rowsMax() != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("returnRows"), f.rowsMax()));
            }

            // executedAt 컬럼 타입이 TEXT(ISO)거나 Instant일 수 있음.
            // TEXT(ISO)여도 문자열 비교가 시간순과 일치하므로 >=, < 비교 가능.
            if (f.from() != null && !f.from().isBlank()) {
                preds.add(cb.greaterThanOrEqualTo(root.get("executedAt"), f.from())); // f.from = "YYYY-MM-DDT00:00:00Z"
            }
            if (f.to() != null && !f.to().isBlank()) {
                preds.add(cb.lessThan(root.get("executedAt"), f.to())); // 상한 '제외'
            }

            return cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
