package com.example.dbids.dto;

public class QueryLogDTO {
    public String executedAt;    // ISO8601 or null(서버가 now로 보정)
    public String userId;        // 필수
    public String adminId;
    public String sqlRaw;        // 필수
    public String sqlSummary;    // 선택
    public Integer returnRows;   // 필수, >= 0
    public String status;        // 필수: SUCCESS | FAILURE

    public QueryLogDTO() {}
    public QueryLogDTO(String executedAt, String userId, String adminId, String sqlRaw, String sqlSummary,
            Integer returnRows, String status) {
        this.executedAt = executedAt; this.userId = userId; this.adminId = adminId; this.sqlRaw = sqlRaw;
        this.sqlSummary = sqlSummary; this.returnRows = returnRows; this.status = status;
    }
}
