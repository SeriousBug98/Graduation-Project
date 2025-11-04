// src/main/java/com/example/dbids/sqlite/model/NotificationLog.java
package com.example.dbids.sqlite.model;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_log")
public class NotificationLog {

    public enum Channel { EMAIL, SLACK }
    public enum Status  { SENT, FAILED }

    @Id
    @Column(length = 36)
    private String id;                   // UUID (NotifyID)

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;              // FK -> detection_event.id

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private Channel channel;             // 'EMAIL' | 'SLACK'

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;               // 'SENT' | 'FAILED'

    @Column(name = "error_code")
    private String errorCode;            // nullable

    @Column(name = "error_message")
    private String errorMessage;         // nullable

    @Column(name = "sent_at", nullable = false, length = 40)
    private String sentAt;               // ISO8601

    protected NotificationLog() {}

    public NotificationLog(String id, String eventId, Channel channel,
            Status status, String errorCode, String errorMessage, String sentAt) {
        this.id = id; this.eventId = eventId; this.channel = channel;
        this.status = status; this.errorCode = errorCode; this.errorMessage = errorMessage;
        this.sentAt = sentAt;
    }

    // getters
    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public Channel getChannel() { return channel; }
    public Status getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getSentAt() { return sentAt; }
}
