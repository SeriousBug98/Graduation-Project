package com.example.dbids.modules.storage;

import com.example.dbids.dto.LogSummaryResponse;
import com.example.dbids.dto.QueryLogFilter;
import com.example.dbids.dto.QueryLogResponse;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.dbids.modules.storage.QueryLogSpecifications.withFilter;

@Service
@Transactional(readOnly = true)
public class LogQueryService {

    private final QueryLogRepository repo;

    @PersistenceContext
    private EntityManager em;

    public LogQueryService(QueryLogRepository repo) {
        this.repo = repo;
    }

    /** 리스트 조회 (페이지네이션) */
    public Page<QueryLogResponse> search(QueryLogFilter f, Pageable pageable) {
        // 1) 날짜 보정 (서비스 내부에서만)
        String fromIso = toUtcStartIso(f.from());
        String toIso   = toUtcEndExclusiveIso(f.to());

        // 2) 스펙 조립 (>= from, < to)
        var spec = withFilter(f.withFrom(fromIso).withTo(toIso));

        Page<QueryLog> page = repo.findAll(spec, pageable);
        List<QueryLogResponse> content = page.getContent().stream()
                .map(QueryLogResponse::fromEntity)
                .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /** CSV 다운로드 (같은 조건) */
    public byte[] exportCsv(QueryLogFilter f, Pageable pageable) {
        String fromIso = toUtcStartIso(f.from());
        String toIso   = toUtcEndExclusiveIso(f.to());
        var spec = withFilter(f.withFrom(fromIso).withTo(toIso));

        Page<QueryLog> page = repo.findAll(spec, pageable);
        StringBuilder sb = new StringBuilder();
        sb.append("executedAt,userId,adminId,sqlRaw,sqlSummary,returnRows,status\n");
        for (QueryLog q : page.getContent()) {
            sb.append(csv(q.getExecutedAt()))
                    .append(',').append(csv(q.getUserId()))
                    .append(',').append(csv(q.getAdminId()))
                    .append(',').append(csv(q.getSqlRaw()))
                    .append(',').append(csv(q.getSqlSummary()))
                    .append(',').append(q.getReturnRows() == null ? "" : q.getReturnRows())
                    .append(',').append(csv(q.getStatus()))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 통계/요약 (상단 KPI + 사용자 Top10 + 시간대별) */
    public LogSummaryResponse summarize(QueryLogFilter f, int limit) {
        String fromIso = toUtcStartIso(f.from());
        String toIso   = toUtcEndExclusiveIso(f.to());

        Map<String, Object> headline = headlineQuery(fromIso, toIso, f);
        List<Map<String, Object>> topUsers = topUsersQuery(fromIso, toIso, f, Math.max(1, Math.min(limit, 100_000)));
        List<Map<String, Object>> byHour = byHourQuery(fromIso, toIso, f);

        // DTO 구성
        LogSummaryResponse resp = new LogSummaryResponse();
        resp.setTotal(((Number) headline.getOrDefault("total", 0)).longValue());
        resp.setTopUser(Objects.toString(headline.get("top_user"), null));
        resp.setPeakHour(Objects.toString(headline.get("peak_hour"), null));
        resp.setTopUsers(topUsers.stream().map(m -> Map.of(
                "user", Objects.toString(m.get("user"), ""),
                "cnt", ((Number)m.get("cnt")).longValue()
        )).collect(Collectors.toList()));
        resp.setByHour(byHour.stream().map(m -> Map.of(
                "hour", Objects.toString(m.get("hour"), ""),
                "cnt", ((Number)m.get("cnt")).longValue()
        )).collect(Collectors.toList()));
        return resp;
    }

    // ---------- Private helpers ----------

    private static String toUtcStartIso(String ymd) {
        if (ymd == null || ymd.isBlank()) return null;
        return LocalDate.parse(ymd).atStartOfDay(ZoneOffset.UTC).toInstant().toString(); // "YYYY-MM-DDT00:00:00Z"
    }

    private static String toUtcEndExclusiveIso(String ymd) {
        if (ymd == null || ymd.isBlank()) return null;
        return LocalDate.parse(ymd).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString(); // (to+1d) 00:00Z
    }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return '"' + v + '"';
    }

    // ---- 네이티브 통계 쿼리 (executed_at은 ISO-8601 Z 텍스트라 문자열 비교 정렬이 시간 순서와 일치) ----

    /** WHERE 공통 조각을 파라미터 유무에 맞게 문자열로 합성해 사용 */
    private static String buildWhereForSummary(QueryLogFilter f) {
        List<String> w = new ArrayList<>();
        w.add("1=1");
        if (f.user() != null && !f.user().isBlank()) w.add("user_id = :user");
        if (f.keywords() != null && !f.keywords().isBlank()) w.add("LOWER(sql_summary) LIKE :kw");
        if (f.status() != null && !f.status().isEmpty()) w.add("status IN (:st)");
        if (f.from() != null) w.add("executed_at >= :fromIso");
        if (f.to() != null)   w.add("executed_at <  :toIso");
        if (f.rowsMin() != null) w.add("return_rows >= :rmin");
        if (f.rowsMax() != null) w.add("return_rows <= :rmax");
        return String.join(" AND ", w);
    }

    private void bindCommonParams(Query q, QueryLogFilter f, String fromIso, String toIso) {
        if (f.user() != null && !f.user().isBlank()) q.setParameter("user", f.user());
        if (f.keywords() != null && !f.keywords().isBlank()) q.setParameter("kw", "%"+f.keywords().toLowerCase()+"%");
        if (f.status() != null && !f.status().isEmpty()) q.setParameter("st", f.status());
        if (fromIso != null) q.setParameter("fromIso", fromIso);
        if (toIso   != null) q.setParameter("toIso", toIso);
        if (f.rowsMin() != null) q.setParameter("rmin", f.rowsMin());
        if (f.rowsMax() != null) q.setParameter("rmax", f.rowsMax());
    }

    private Map<String,Object> headlineQuery(String fromIso, String toIso, QueryLogFilter f) {
        String where = buildWhereForSummary(f.withFrom(fromIso).withTo(toIso));
        String sql = """
            WITH base AS ( SELECT * FROM query_log WHERE %s ),
                 user_cnt AS ( SELECT user_id AS user, COUNT(*) AS cnt FROM base GROUP BY user ),
                 hour_cnt AS ( SELECT substr(executed_at,1,13) AS hr, COUNT(*) AS cnt FROM base GROUP BY hr )
            SELECT
              (SELECT COUNT(*) FROM base) AS total,
              (SELECT user FROM user_cnt ORDER BY cnt DESC, user ASC LIMIT 1) AS top_user,
              (SELECT hr || ':00:00Z' FROM hour_cnt ORDER BY cnt DESC, hr ASC LIMIT 1) AS peak_hour
            """.formatted(where);
        Query q = em.createNativeQuery(sql);
        bindCommonParams(q, f, fromIso, toIso);
        Object[] row = (Object[]) q.getSingleResult();
        Map<String,Object> m = new HashMap<>();
        m.put("total", row[0] == null ? 0 : ((Number)row[0]).longValue());
        m.put("top_user", row[1]);
        m.put("peak_hour", row[2]);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> topUsersQuery(String fromIso, String toIso, QueryLogFilter f, int limit) {
        String where = buildWhereForSummary(f.withFrom(fromIso).withTo(toIso));
        String sql = """
            SELECT user_id AS user, COUNT(*) AS cnt
            FROM query_log
            WHERE %s
            GROUP BY user_id
            ORDER BY cnt DESC
            LIMIT :lim
            """.formatted(where);
        Query q = em.createNativeQuery(sql);
        bindCommonParams(q, f, fromIso, toIso);
        q.setParameter("lim", limit);
        List<Object[]> rows = q.getResultList();
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(Map.of("user", r[0], "cnt", ((Number)r[1]).longValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> byHourQuery(String fromIso, String toIso, QueryLogFilter f) {
        String where = buildWhereForSummary(f.withFrom(fromIso).withTo(toIso));
        String sql = """
            SELECT substr(executed_at,1,13) || ':00:00Z' AS hour, COUNT(*) AS cnt
            FROM query_log
            WHERE %s
            GROUP BY substr(executed_at,1,13)
            ORDER BY hour ASC
            """.formatted(where);
        Query q = em.createNativeQuery(sql);
        bindCommonParams(q, f, fromIso, toIso);
        List<Object[]> rows = q.getResultList();
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(Map.of("hour", r[0], "cnt", ((Number)r[1]).longValue()));
        }
        return out;
    }
}
