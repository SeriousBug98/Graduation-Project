package com.example.dbids.sqlite.model;

import jakarta.persistence.*;

@Entity
@Table(name = "query_log", indexes = {
        @Index(name = "idx_exec_time", columnList = "executed_at")
})
public class QueryLog {

    public enum Status { SUCCESS, FAILURE }

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "executed_at", nullable = false)
    private String executedAt;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "admin_id")
    private String adminId;

    @Column(name = "sql_raw", nullable = false, columnDefinition = "TEXT")
    private String sqlRaw;

    @Column(name = "sql_summary")
    private String sqlSummary;

    @Column(name = "return_rows", nullable = false)
    private int returnRows;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = true)
    private Status status;

    protected QueryLog() {}

    public QueryLog(String id, String executedAt, String userId,
            String adminId, String sqlRaw, String sqlSummary,
            int returnRows, Status status) {
        this.id = id;
        this.executedAt = executedAt;
        this.userId = userId;
        this.adminId = adminId;
        this.sqlRaw = sqlRaw;
        this.sqlSummary = sqlSummary;
        this.returnRows = returnRows;
        this.status = status;
    }

    public String getId() { return id; }
    public String getExecutedAt() { return executedAt; }
    public String getUserId() { return userId; }
    public String getAdminId() { return adminId; }
    public String getSqlRaw() { return sqlRaw; }
    public String getSqlSummary() { return sqlSummary; }
    public int getReturnRows() { return returnRows; }
    public Status getStatus() { return status; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public void setSqlSummary(String sqlSummary) { this.sqlSummary = sqlSummary; }
    public void setStatus(Status status) { this.status = status; }
}
