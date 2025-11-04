package com.example.dbids.modules.authz;

import com.example.dbids.modules.behavior.QueryClassifier;
import com.example.dbids.modules.rule.SqlNormalizer;
import com.example.dbids.sqlite.model.DetectionEvent;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 정책 매칭:
 * - 입력: userId, sqlRaw
 * - 처리: normalize → action + tables 추출 → role 정책(deny > allow) 비교
 * - 출력: 위반 시 Violation 리턴
 */
public class AuthZEngine {

    public enum Action { SELECT, INSERT, UPDATE, DELETE, DDL, OTHER }

    public record Violation(
            DetectionEvent.Severity severity,
            String reason,
            Action action,
            String tableMatched,
            String ruleMatched // 예: "DENY DELETE:*"
    ) {}

    private final AuthZProperties props;
    private final SqlNormalizer normalizer = new SqlNormalizer();
    private final QueryClassifier classifier = new QueryClassifier();

    public AuthZEngine(AuthZProperties props) {
        this.props = props;
    }

    /** 주어진 SQL에 대한 권한 위반을 평가한다. 위반 없으면 empty */
    public Optional<Violation> evaluate(String userId, String sqlRaw) {
        String roleName = props.getUserRoles() == null ? null : props.getUserRoles().get(userId);
        if (roleName == null) return Optional.empty(); // 롤 미정의 → 정책 비적용(허용)
        AuthZProperties.Role role = props.getRoles() == null ? null : props.getRoles().get(roleName);
        if (role == null) return Optional.empty();

        String norm = normalizer.normalize(sqlRaw);
        Action action = toAction(norm);
        String[] tables = classifier.guessTables(norm);
        if (tables.length == 0) tables = new String[] {"*"}; // 테이블 파싱 못하면 * 로 평가

        // Deny 우선
        var deny = safe(role.getDeny());
        var allow = safe(role.getAllow());

        // 각 테이블에 대해 규칙 평가
        for (String t : tables) {
            String key = actionName(action) + ":" + t.toUpperCase(Locale.ROOT);
            if (matches(deny, action, t)) {
                String ruleStr = matched(deny, action, t);
                return Optional.of(new Violation(role.getDefaultSeverity(),
                        "AUTHZ_DENY for " + key, action, t, "DENY " + ruleStr));
            }
            // allow는 “허용 근거”만 제공. allow에 없어도 기본은 deny가 아님(보수 정책은 필요시 deny에 넣음)
            if (matches(allow, action, t)) {
                // 명시 허용 → 다음 테이블 평가
                continue;
            }
        }

        // 정책상 명시 deny가 없으면 허용으로 본다(READ_ONLY 등은 deny를 반드시 선언)
        return Optional.empty();
    }

    // ----- helpers -----

    private static List<String> safe(List<String> xs) { return xs == null ? List.of() : xs; }

    private static String actionName(Action a) { return a.name(); }

    /** "ACTION:TABLEPAT" 리스트에서 현재 액션/테이블과 매칭되는 항목이 있으면 true */
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

        // TABLE 매칭: * 또는 와일드카드(간단히 *만 지원). 대소문자 무시.
        if (rt.equals("*")) return true;
        String tbl = table.toUpperCase(Locale.ROOT);
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
