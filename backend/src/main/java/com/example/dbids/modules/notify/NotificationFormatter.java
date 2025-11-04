package com.example.dbids.modules.notify;

import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.QueryLog;
import org.springframework.stereotype.Component;

@Component
public class NotificationFormatter {

    public String emailSubject(DetectionEvent ev, QueryLog log) {
        String user = (log != null && log.getUserId() != null) ? log.getUserId() : "-";
        return "[DB-IDS] [" + ev.getSeverity() + "] Type=" + ev.getEventType() + " User=" + user;
    }

    public String emailBody(DetectionEvent ev, QueryLog log) {
        StringBuilder sb = new StringBuilder();
        sb.append("🚨 DB-IDS Detection Alert\n\n")
                .append("Event ID : ").append(ev.getId()).append('\n')
                .append("Log ID   : ").append(ev.getLogId()).append('\n')
                .append("Type     : ").append(ev.getEventType()).append('\n')
                .append("Severity : ").append(ev.getSeverity()).append('\n')
                .append("Time     : ").append(ev.getOccurredAt()).append('\n');

        if (log != null) {
            sb.append("\nUser     : ").append(nz(log.getUserId(), "-")).append('\n')
                    .append("Status   : ").append(log.getStatus()).append('\n')
                    .append("Rows     : ").append(log.getReturnRows()).append('\n')
                    .append("\nSQL Summary:\n").append(nz(log.getSqlSummary(), "-")).append('\n')
                    .append("\nSQL Raw:\n").append(nz(log.getSqlRaw(), "-")).append('\n');
        }
        return sb.toString();
    }

    private static String nz(String s, String d) { return (s == null || s.isBlank()) ? d : s; }
}
