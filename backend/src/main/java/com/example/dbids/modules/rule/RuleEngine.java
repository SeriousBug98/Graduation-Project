package com.example.dbids.modules.rule;

import com.example.dbids.sqlite.model.DetectionEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RuleEngine {

    public static class Match {
        public final String ruleId;
        public final DetectionEvent.Severity severity;
        public Match(String ruleId, DetectionEvent.Severity severity) {
            this.ruleId = ruleId;
            this.severity = severity;
        }
    }

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules;
    }

    /**
     * 여러 룰이 동시에 매칭되면 severity가 가장 높은 것 1건만 채택.
     */
    public Optional<Match> evaluate(String normalizedSql) {
        return rules.stream()
                .filter(r -> r.matches(normalizedSql))
                .map(r -> new Match(r.id, r.severity))
                .max(Comparator.comparing(m -> m.severity)); // enum 순서: LOW < MEDIUM < HIGH
    }
}
