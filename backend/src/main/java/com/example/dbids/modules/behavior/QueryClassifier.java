package com.example.dbids.modules.behavior;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryClassifier {

    public enum Kind { SELECT, INSERT, UPDATE, DELETE, DDL, OTHER }

    public Kind classify(String normalizedSql) {
        if (normalizedSql == null || normalizedSql.isBlank()) return Kind.OTHER;
        String s = normalizedSql.trim();
        if (s.startsWith("SELECT")) return Kind.SELECT;
        if (s.startsWith("INSERT")) return Kind.INSERT;
        if (s.startsWith("UPDATE")) return Kind.UPDATE;
        if (s.startsWith("DELETE")) return Kind.DELETE;
        if (s.startsWith("CREATE") || s.startsWith("ALTER") || s.startsWith("DROP")
                || s.startsWith("TRUNCATE") || s.startsWith("RENAME")) return Kind.DDL;
        return Kind.OTHER;
    }

    /** 매우 단순한 테이블명 추정(프리뷰용) */
    public String[] guessTables(String normalizedSql) {
        if (normalizedSql == null) return new String[0];
        Set<String> names = new HashSet<>();
        Pattern p = Pattern.compile("\\b(FROM|INTO|UPDATE|TABLE)\\s+([A-Z0-9_.`]+)");
        Matcher m = p.matcher(normalizedSql);
        while (m.find()) {
            names.add(m.group(2));
        }
        return names.toArray(new String[0]);
    }
}
