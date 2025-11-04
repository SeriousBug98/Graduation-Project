package com.example.dbids.dto;

import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.QueryLog;

import java.util.List;

public class EventDtos {

    public static class EventSummary {
        public String id;
        public String logId;
        public String eventType;   // PATTERN | BEHAVIOR | AUTHZ
        public String severity;    // LOW | MEDIUM | HIGH
        public String occurredAt;  // ISO-8601
        public String userId;      // 이메일(쿼리 실행 사용자)
        public String adminId;     // 관리자의 UUID
        public String sqlPreview;  // sql_raw 앞부분 160자

        public EventSummary() {}
        public EventSummary(DetectionEvent ev, QueryLog ql) {
            this.id = ev.getId();
            this.logId = ev.getLogId();
            this.eventType = ev.getEventType().name();
            this.severity = ev.getSeverity().name();
            this.occurredAt = ev.getOccurredAt();
            this.userId = (ql != null ? ql.getUserId() : null);
            this.adminId = (ql != null ? ql.getAdminId() : null);
            String src = ev.getSqlRaw() == null ? "" : ev.getSqlRaw();
            this.sqlPreview = src.length() > 160 ? src.substring(0,160) : src;
        }
    }

    public static class EventDetail {
        public String id;
        public String logId;
        public String eventType;
        public String severity;
        public String occurredAt;
        public String sqlRaw;      // 정규화/마스킹된 원문

        // QueryLog snapshot
        public String executedAt;
        public String userId;
        public String adminId;
        public String sqlSummary;
        public Integer returnRows;
        public String status; // SUCCESS | FAILURE

        public EventDetail() {}
        public EventDetail(DetectionEvent ev, QueryLog ql) {
            this.id = ev.getId();
            this.logId = ev.getLogId();
            this.eventType = ev.getEventType().name();
            this.severity = ev.getSeverity().name();
            this.occurredAt = ev.getOccurredAt();
            this.sqlRaw = ev.getSqlRaw();
            if (ql != null) {
                this.executedAt = ql.getExecutedAt();
                this.userId = ql.getUserId();
                this.adminId = ql.getAdminId();
                this.sqlSummary = ql.getSqlSummary();
                this.returnRows = ql.getReturnRows();
                this.status = ql.getStatus().name();
            }
        }
    }

    public static class Page<T> {
        public List<T> content;
        public int page;
        public int size;
        public long totalElements;
        public int totalPages;

        public Page() {}
        public Page(List<T> content, int page, int size, long totalElements) {
            this.content = content;
            this.page = page;
            this.size = size;
            this.totalElements = totalElements;
            this.totalPages = (int) Math.ceil(totalElements / (double) Math.max(size,1));
        }
    }
}
