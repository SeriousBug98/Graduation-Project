package com.example.dbids.modules.storage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlSummarizer {
    private SqlSummarizer() {}

    // 간단 정규식
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|--.*?(\\r?\\n|$)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LITERALS = Pattern.compile("'[^']*'|\"[^\"]*\"|\\b\\d+\\b");

    public static String summarize(String sqlRaw) {
        if (sqlRaw == null || sqlRaw.isBlank()) return "";
        String s = COMMENTS.matcher(sqlRaw).replaceAll(" ");
        s = WHITESPACE.matcher(s).replaceAll(" ").trim();

        Pattern p = Pattern.compile("(?i)\\b(select|from|where|group by|order by|limit|insert|into|values|update|set|delete|join|left|right|inner|outer|on)\\b");
        Matcher m = p.matcher(s);

        // 키워드만 대충 대문자
        s = m.replaceAll(mr -> mr.group().toUpperCase());

        // 리터럴 축약
        String sForSummary = LITERALS.matcher(s).replaceAll("?");

        // 대표 동사/테이블 추출
        String verb = firstGroup(sForSummary, "^(SELECT|INSERT|UPDATE|DELETE)\\b");
        String table = "";
        if ("SELECT".equals(verb))   table = firstGroup(sForSummary, "\\bFROM\\s+([\\w.\\-`]+)");
        if ("INSERT".equals(verb))   table = firstGroup(sForSummary, "\\bINTO\\s+([\\w.\\-`]+)");
        if ("UPDATE".equals(verb))   table = firstGroup(sForSummary, "\\bUPDATE\\s+([\\w.\\-`]+)");
        if ("DELETE".equals(verb))   table = firstGroup(sForSummary, "\\bFROM\\s+([\\w.\\-`]+)");

        String where = firstGroup(sForSummary, "\\bWHERE\\b(.*?)(GROUP BY|ORDER BY|LIMIT|$)");
        String base = (verb == null ? "" : verb) + (table == null || table.isBlank() ? "" : " " + table);

        StringBuilder sb = new StringBuilder();
        if (!base.isBlank()) sb.append(base);
        if (where != null && !where.isBlank()) {
            String w = where.trim();
            // 너무 길면 자르기
            if (w.length() > 120) w = w.substring(0, 117) + "...";
            if (!base.isBlank()) sb.append(" ");
            sb.append("WHERE ").append(w);
        }
        String summary = sb.toString();
        if (summary.isBlank()) summary = sForSummary;
        if (summary.length() > 160) summary = summary.substring(0, 157) + "...";
        return summary;
    }

    private static String firstGroup(String s, String regex) {
        var m = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
        return m.find() ? m.group(1).trim() : null;
    }
}
