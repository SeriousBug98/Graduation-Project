// src/main/java/com/example/dbids/dto/QueryLogResponse.java
package com.example.dbids.dto;

import com.example.dbids.sqlite.model.QueryLog;

public class QueryLogResponse {
    public String id;
    public String executedAt;
    public String userId;
    public String sqlSummary;
    public String sqlRaw;
    public int    returnRows;
    public String status;   // enum -> String으로 내려줌

    public QueryLogResponse(String id, String executedAt, String userId,
            String sqlSummary, String sqlRaw, int returnRows, String status) {
        this.id = id;
        this.executedAt = executedAt;
        this.userId = userId;
        this.sqlSummary = sqlSummary;
        this.sqlRaw = sqlRaw;
        this.returnRows = returnRows;
        this.status = status;
    }

    /** 엔티티 -> DTO 표준 변환 */
    public static QueryLogResponse of(QueryLog q) {
        return new QueryLogResponse(
                q.getId(),                         // String or toString()
                q.getExecutedAt(),                 // ISO-8601 string (엔티티 그대로)
                q.getUserId(),
                q.getSqlSummary(),
                q.getSqlRaw(),
                q.getReturnRows(),
                q.getStatus() == null ? null : q.getStatus().name()
        );
    }
}
