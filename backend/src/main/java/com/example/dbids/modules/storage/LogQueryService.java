// src/main/java/com/example/dbids/modules/storage/LogQueryService.java
package com.example.dbids.modules.storage;

import com.example.dbids.dto.LogSummaryResponse;
import com.example.dbids.dto.QueryLogFilter;
import com.example.dbids.dto.QueryLogResponse;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.model.QueryLog.Status;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.dbids.modules.storage.QueryLogSpecs.*;

@Service
public class LogQueryService {

    private final QueryLogRepository repo;

    public LogQueryService(QueryLogRepository repo) {
        this.repo = repo;
    }

    public Page<QueryLogResponse> search(QueryLogFilter f, Pageable pageable) {
        Specification<QueryLog> spec = Specification
                .where(userEqualsIgnoreCase(f.user))
                .and(statusIn(f.status))
                .and(summaryLike(f.keywords))
                .and(executedBetween(f.from, f.to))
                .and(rowsBetween(f.rowsMin, f.rowsMax));

        Page<QueryLog> page = repo.findAll(spec, pageable);
        return page.map(this::toResp);
    }

    public byte[] exportCsv(QueryLogFilter f, Pageable pageable) {
        var page = search(f, pageable);
        StringBuilder sb = new StringBuilder();
        sb.append("id,executedAt,userId,sqlSummary,sqlRaw,returnRows,status\n");
        for (var r : page.getContent()) {
            sb.append(csv(r.id)).append(',')
                    .append(csv(r.executedAt)).append(',')
                    .append(csv(r.userId)).append(',')
                    .append(csv(r.sqlSummary)).append(',')
                    .append(csv(r.sqlRaw)).append(',')
                    .append(csv(r.returnRows)).append(',')
                    .append(csv(r.status)).append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private QueryLogResponse toResp(QueryLog q) {
        String summary = (q.getSqlSummary() != null && !q.getSqlSummary().isBlank())
                ? q.getSqlSummary()
                : SqlSummarizer.summarize(q.getSqlRaw());

        return new com.example.dbids.dto.QueryLogResponse(
                q.getId(),
                q.getExecutedAt(),
                q.getUserId(),
                summary,
                q.getSqlRaw(),
                q.getReturnRows(),
                q.getStatus().name()
        );
    }

    private String csv(Object o) {
        if (o == null) return "";
        String s = o.toString().replace("\"","\"\"");
        if (s.contains(",") || s.contains("\n")) return "\"" + s + "\"";
        return s;
    }

    public LogSummaryResponse summarize(QueryLogFilter f, int limit) {
        Specification<QueryLog> spec = Specification
                .where(userEqualsIgnoreCase(f.user))
                .and(statusIn(f.status))
                .and(summaryLike(f.keywords))
                .and(executedBetween(f.from, f.to))
                .and(rowsBetween(f.rowsMin, f.rowsMax));

        // 너무 큰 데이터 방지용 최대치 (필요하면 페이징 반복으로 확장 가능)
        List<QueryLog> all = repo.findAll(spec, Sort.by(Sort.Direction.ASC, "executedAt"));
        if (limit > 0 && all.size() > limit) {
            all = all.subList(0, limit);
        }

        // 사용자 버킷
        Map<String, long[]> userAgg = new HashMap<>();
        // 시간대 버킷 (yyyy-MM-ddTHH:00 형태)
        Map<String, long[]> timeAgg = new HashMap<>();

        for (QueryLog q : all) {
            String userId = nz(q.getUserId());
            String hourKey = hourBucket(q.getExecutedAt());
            boolean ok = q.getStatus() == Status.SUCCESS;
            int rows = q.getReturnRows();

            // users
            long[] u = userAgg.computeIfAbsent(userId, k -> new long[4]);
            u[0]++;                 // total
            if (ok) u[1]++; else u[2]++; // success/failure
            u[3] += rows;           // rowsSum

            // times
            long[] t = timeAgg.computeIfAbsent(hourKey, k -> new long[4]);
            t[0]++;
            if (ok) t[1]++; else t[2]++;
            t[3] += rows;
        }

        List<LogSummaryResponse.UserBucket> users = userAgg.entrySet().stream()
                .map(e -> new LogSummaryResponse.UserBucket(
                        e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .sorted(Comparator.comparingLong((LogSummaryResponse.UserBucket b) -> b.total).reversed())
                .collect(Collectors.toList());

        List<LogSummaryResponse.TimeBucket> times = timeAgg.entrySet().stream()
                .map(e -> new LogSummaryResponse.TimeBucket(
                        e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .sorted(Comparator.comparing(tb -> tb.hour)) // 시간 문자열 오름차순
                .collect(Collectors.toList());

        return new LogSummaryResponse(users, times);
    }

    private static String nz(String s) { return (s == null || s.isBlank()) ? "(unknown)" : s; }

    /** "YYYY-MM-DDTHH:MM" 버킷 키 생성. executedAt이 ISO-8601 문자열이라고 가정 */
    private static String hourBucket(String executedAt) {
        if (executedAt == null) return "(null)";
        // 최소 "yyyy-MM-ddTHH" 길이 확인
        if (executedAt.length() >= 13) {
            return executedAt.substring(0, 13) + ":00";
        }
        return executedAt; // 이상치 그대로
    }
}
