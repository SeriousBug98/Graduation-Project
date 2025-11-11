// 예시: QueryLogFilter.java (record)
package com.example.dbids.dto;

import java.util.List;

public record QueryLogFilter(
        String user,
        String keywords,
        List<String> status,
        String from,   // 문자열(ISO)
        String to,     // 문자열(ISO, exclusive로 넘김)
        Integer rowsMin,
        Integer rowsMax
) {
    public QueryLogFilter withFrom(String newFrom) {
        return new QueryLogFilter(user, keywords, status, newFrom, to, rowsMin, rowsMax);
    }
    public QueryLogFilter withTo(String newTo) {
        return new QueryLogFilter(user, keywords, status, from, newTo, rowsMin, rowsMax);
    }
}
