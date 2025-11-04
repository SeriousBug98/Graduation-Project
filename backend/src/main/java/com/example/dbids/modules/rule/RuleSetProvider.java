package com.example.dbids.modules.rule;

import com.example.dbids.sqlite.model.DetectionEvent;
import java.util.List;

public class RuleSetProvider {

    // SRS/SDS의 대표 패턴: DROP TABLE, TRUNCATE, UNION SELECT, OR 1=1, SLEEP(...)
    public static List<Rule> defaultRules() {
        return List.of(
            new Rule("PATTERN_DROP_TABLE",  DetectionEvent.Severity.HIGH,   "\\bDROP\\s+TABLE\\b"),
            new Rule("PATTERN_TRUNCATE",    DetectionEvent.Severity.HIGH,   "\\bTRUNCATE\\b"),
            new Rule("PATTERN_UNION_SELECT",DetectionEvent.Severity.MEDIUM, "\\bUNION\\s+SELECT\\b"),
            new Rule("PATTERN_OR_1_EQ_1",    DetectionEvent.Severity.MEDIUM, "\\bOR\\s+(?:1|0)\\s*=\\s*(?:1|0)\\b"),
            new Rule("PATTERN_SLEEP_FUNC",  DetectionEvent.Severity.LOW,    "\\bSLEEP\\s*\\(")
        );
    }
}
