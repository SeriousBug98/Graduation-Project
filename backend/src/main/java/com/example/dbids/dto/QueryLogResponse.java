// src/main/java/com/example/dbids/dto/QueryLogResponse.java
package com.example.dbids.dto;

public class QueryLogResponse {
    public String id;
    public String executedAt;
    public String userId;
    public String sqlSummary;
    public String sqlRaw;
    public int returnRows;
    public String status;

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
}
