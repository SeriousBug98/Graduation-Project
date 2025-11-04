package com.example.dbids.sqlite.model;

import jakarta.persistence.*;

@Entity
@Table(name = "detection_event")
public class DetectionEvent {

    public enum Type { PATTERN, BEHAVIOR, AUTHZ }
    public enum Severity { LOW, MEDIUM, HIGH }

    @Id
    @Column(length = 36)
    private String id;                // UUID(EventID)

    @Column(name = "log_id", nullable = false, length = 36)
    private String logId;             // UUID(FK → QueryLog.id)

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private Type eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Column(name = "occurred_at", nullable = false, length = 40)
    private String occurredAt;        // ISO8601

    @Lob
    @Column(name = "sql_raw", nullable = false)
    private String sqlRaw;            // 정규화/마스킹 적용 SQL

    public DetectionEvent() {}

    public DetectionEvent(String id, String logId, Type eventType,
            Severity severity, String occurredAt, String sqlRaw) {
        this.id = id;
        this.logId = logId;
        this.eventType = eventType;
        this.severity = severity;
        this.occurredAt = occurredAt;
        this.sqlRaw = sqlRaw;
    }

    public String getId() { return id; }
    public String getLogId() { return logId; }
    public Type getEventType() { return eventType; }
    public Severity getSeverity() { return severity; }
    public String getOccurredAt() { return occurredAt; }
    public String getSqlRaw() { return sqlRaw; }


    public void setId(String id) { this.id = id; }
    public void setLogId(String logId) { this.logId = logId; }
    public void setEventType(Type eventType) { this.eventType = eventType; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
    public void setSqlRaw(String sqlRaw) { this.sqlRaw = sqlRaw; }
}
