// src/main/java/com/example/dbids/modules/authz/AuthZEngine.java
package com.example.dbids.modules.authz;

import com.example.dbids.modules.behavior.QueryClassifier;
import com.example.dbids.modules.rule.SqlNormalizer;
import com.example.dbids.sqlite.model.DetectionEvent;

import java.util.*;
import java.util.regex.Pattern;

public class AuthZEngine {

    public enum Action { SELECT, INSERT, UPDATE, DELETE, DDL, OTHER }

    public record Violation(
            DetectionEvent.Severity severity,
            String reason,
            Action action,
            String tableMatched,
            String ruleMatched
    ) {}

    private final AuthZProperties props;
    private final SqlNormalizer normalizer = new SqlNormalizer();
    private final QueryClassifier classifier = new QueryClassifier();

    public AuthZEngine(AuthZProperties props) {
        this.props = props;
    }

    public Optional<Violation> evaluate(String userIdRaw, String sqlRaw) {
        // userIdRaw는 그대로 두고, 매칭만 내부적으로 케이스 무시/오타 우회
        String roleName = props.resolveRoleName(userIdRaw);
        if (roleName == null) {
            System.out.println("[AUTHZ] no role for user=" + userIdRaw + " (roleName=null)");
            return Optional.empty();
        }

        AuthZProperties.Role role = (props.getRoles() == null) ? null : props.getRoles().get(roleName);
        if (role == null) {
            System.out.println("[AUTHZ] role object not found for name=" + roleName);
            return Optional.empty();
        }

        String norm = normalizer.normalize(sqlRaw);
        Action action = toAction(norm);
        String[] tables = classifier.guessTables(norm);
        if (tables == null || tables.length == 0) tables = new String[] {"*"};

        List<String> deny = role.getDeny() == null ? List.of() : role.getDeny();
        List<String> allow = role.getAllow() == null ? List.of() : role.getAllow();

        for (String t : tables) {
            if (matches(deny, action, t)) {
                String ruleStr = matched(deny, action, t);
                System.out.println("[AUTHZ] DENY matched user=" + userIdRaw + " action=" + action + " table=" + t + " rule=" + ruleStr);
                return Optional.of(new Violation(
                        role.getDefaultSeverity(),
                        "AUTHZ_DENY for " + action.name() + ":" + t,
                        action,
                        t,
                        "DENY " + ruleStr
                ));
            }
            if (matches(allow, action, t)) {
                System.out.println("[AUTHZ] ALLOW matched user=" + userIdRaw + " action=" + action + " table=" + t);
            }
        }
        System.out.println("[AUTHZ] EMPTY (no event) user=" + userIdRaw);
        return Optional.empty();
    }

    private static boolean matches(List<String> rules, Action action, String table) {
        return rules.stream().anyMatch(r -> oneMatch(r, action, table));
    }
    private static String matched(List<String> rules, Action action, String table) {
        return rules.stream().filter(r -> oneMatch(r, action, table)).findFirst().orElse("");
    }
    private static boolean oneMatch(String rule, Action action, String table) {
        String[] parts = rule.split(":", 2);
        if (parts.length != 2) return false;
        String ra = parts[0].trim().toUpperCase(Locale.ROOT);
        String rt = parts[1].trim();
        if (!ra.equals(action.name())) return false;
        if (rt.equals("*")) return true;
        String tbl = (table == null ? "" : table).toUpperCase(Locale.ROOT);
        String pat = rt.toUpperCase(Locale.ROOT)
                .replace(".", "\\.")
                .replace("`", "")
                .replace("*", ".*");
        return Pattern.compile("^" + pat + "$").matcher(tbl).matches();
    }

    private static Action toAction(String normalized) {
        if (normalized == null || normalized.isBlank()) return Action.OTHER;
        String s = normalized.trim();
        if (s.startsWith("SELECT")) return Action.SELECT;
        if (s.startsWith("INSERT")) return Action.INSERT;
        if (s.startsWith("UPDATE")) return Action.UPDATE;
        if (s.startsWith("DELETE")) return Action.DELETE;
        if (s.startsWith("CREATE") || s.startsWith("ALTER") || s.startsWith("DROP")
                || s.startsWith("TRUNCATE") || s.startsWith("RENAME")) return Action.DDL;
        return Action.OTHER;
    }
}
