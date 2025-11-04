package com.example.dbids.modules.rule;

import com.example.dbids.sqlite.model.DetectionEvent;
import java.util.regex.Pattern;

public class Rule {
    public final String id;
    public final DetectionEvent.Severity severity;
    private final Pattern pattern;

    public Rule(String id, DetectionEvent.Severity severity, String regex) {
        this.id = id;
        this.severity = severity;
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    public boolean matches(String normalizedSql) {
        return pattern.matcher(normalizedSql).find();
    }
}
