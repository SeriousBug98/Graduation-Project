// src/main/java/com/example/dbids/dto/QueryLogFilter.java
package com.example.dbids.dto;

import java.util.List;

public class QueryLogFilter {
    public String user;          // userId
    public String keywords;      // sqlSummary LIKE
    public List<String> status;  // SUCCESS/FAILURE
    public String from;          // ISO-8601 문자열
    public String to;            // ISO-8601 문자열
    public Integer rowsMin;      // returnRows >=
    public Integer rowsMax;      // returnRows <=

    public QueryLogFilter() {}
    public QueryLogFilter(String user, String keywords, List<String> status,
            String from, String to, Integer rowsMin, Integer rowsMax) {
        this.user = user; this.keywords = keywords; this.status = status;
        this.from = from; this.to = to; this.rowsMin = rowsMin; this.rowsMax = rowsMax;
    }
}
