package com.example.dbids.modules.notify;

import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.QueryLog;
import org.springframework.stereotype.Component;

@Component
public class SlackFormatter {

    /** 간단한 Markdown 포맷. 코드블럭에 SQL 포함 */
    public String text(DetectionEvent ev, QueryLog log) {
        StringBuilder sb = new StringBuilder();
        sb.append(":rotating_light: *DB-IDS Detection Alert* :rotating_light:\n")
                .append("*Severity*: ").append(ev.getSeverity()).append("  ")
                .append("*Type*: ").append(ev.getEventType()).append('\n')
                .append("*Event ID*: ").append(ev.getId()).append("  ")
                .append("*Log ID*: ").append(ev.getLogId()).append('\n')
                .append("*Time*: ").append(ev.getOccurredAt()).append('\n');

        if (log != null) {
            sb.append("*User*: ").append(nz(log.getUserId(), "-")).append("  ")
                    .append("*Status*: ").append(log.getStatus()).append("  ")
                    .append("*Rows*: ").append(log.getReturnRows()).append('\n');

            String summary = nz(log.getSqlSummary(), "-");
            String raw = nz(log.getSqlRaw(), "-");

            if (!summary.isBlank() && !summary.equals("-")) {
                sb.append("\n*SQL Summary:*\n```").append(cut(summary, 1000)).append("```\n");
            }
            if (!raw.isBlank() && !raw.equals("-")) {
                sb.append("*SQL Raw:*\n```").append(cut(raw, 2000)).append("```\n");
            }
        }
        return sb.toString();
    }

    private static String nz(String s, String d) { return (s == null || s.isBlank()) ? d : s; }
    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
