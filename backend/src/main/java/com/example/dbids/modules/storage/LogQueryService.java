package com.example.dbids.modules.storage;

import com.example.dbids.dto.LogSummaryResponse;
import com.example.dbids.dto.QueryLogFilter;
import com.example.dbids.dto.QueryLogResponse;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static com.example.dbids.modules.storage.QueryLogSpecifications.withFilter;

@Service
@Transactional(readOnly = true)
public class LogQueryService {

    private final QueryLogRepository repo;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    @PersistenceContext
    private EntityManager em;

    public LogQueryService(QueryLogRepository repo) {
        this.repo = repo;
    }

    /** 리스트 조회 (페이지네이션) */
    public Page<QueryLogResponse> search(QueryLogFilter f, Pageable pageable) {
        // 날짜 보정: [from 00:00Z, (to+1d) 00:00Z)
        String fromIso = toUtcStartIso(f.from());
        String toIso   = toUtcEndExclusiveIso(f.to());

        var spec = withFilter(f.withFrom(fromIso).withTo(toIso));

        Page<QueryLog> page = repo.findAll(spec, pageable);

        // ❗ QueryLogResponse::fromEntity 가 없으므로, 직접 매핑
        return new PageImpl<>(
                page.getContent().stream()
                        .map(q -> {
                            String summary = (q.getSqlSummary() != null && !q.getSqlSummary().isBlank())
                                    ? q.getSqlSummary()
                                    : com.example.dbids.modules.storage.SqlSummarizer.summarize(q.getSqlRaw());
                            return new com.example.dbids.dto.QueryLogResponse(
                                    q.getId(),
                                    q.getExecutedAt(),
                                    q.getUserId(),
                                    summary,
                                    q.getSqlRaw(),
                                    q.getReturnRows(),
                                    q.getStatus() == null ? null : q.getStatus().name()
                            );
                        })
                        .toList(),
                pageable,
                page.getTotalElements()
        );
    }

    /** CSV 다운로드 */
    public byte[] exportCsv(QueryLogFilter f, Pageable pageable) {
        String fromIso = toUtcStartIso(f.from());
        String toIso   = toUtcEndExclusiveIso(f.to());
        var spec = withFilter(f.withFrom(fromIso).withTo(toIso));

        Page<QueryLog> page = repo.findAll(spec, pageable);

        StringBuilder sb = new StringBuilder();
        sb.append("executedAt,userId,adminId,sqlRaw,sqlSummary,returnRows,status\n");
        for (QueryLog q : page.getContent()) {
            sb.append(csv(q.getExecutedAt()))
                    .append(',').append(csv(nullToEmpty(q.getUserId())))
                    .append(',').append(csv(nullToEmpty(q.getAdminId())))
                    .append(',').append(csv(nullToEmpty(q.getSqlRaw())))
                    .append(',').append(csv(nullToEmpty(q.getSqlSummary())))
                    // returnRows 는 int(primitive) 라 null 비교 불가
                    .append(',').append(q.getReturnRows())
                    // enum → 문자열은 name() 사용
                    .append(',').append(csv(q.getStatus() == null ? "" : q.getStatus().name()))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 통계/요약 */
    public LogSummaryResponse summarize(QueryLogFilter f, int limit) {
        String fromIso = toUtcStartIso(f.from());
        String toIso   = toUtcEndExclusiveIso(f.to());

        List<Map<String, Object>> topUsers = topUsersQuery(fromIso, toIso, f, Math.max(1, Math.min(limit, 100_000)));
        List<Map<String, Object>> byHour   = byHourQuery(fromIso, toIso, f);

        // ⬇️ DTO 시그니처에 의존하지 않고 안전하게 생성
        List<LogSummaryResponse.UserBucket> userBuckets = topUsers.stream()
                .map(m -> makeUserBucket(
                        Objects.toString(m.get("user"), ""),
                        ((Number) m.get("cnt")).longValue()
                ))
                .toList();

        List<LogSummaryResponse.TimeBucket> timeBuckets = byHour.stream()
                .map(m -> makeTimeBucket(
                        Objects.toString(m.get("hour"), ""),
                        ((Number) m.get("cnt")).longValue()
                ))
                .toList();

        return new LogSummaryResponse(userBuckets, timeBuckets);
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

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    // ---- 네이티브 통계 쿼리 (>= fromIso AND < toIso) ----

    private static String buildWhereForSummary(QueryLogFilter f) {
        List<String> w = new ArrayList<>();
        w.add("1=1");
        if (f.user() != null && !f.user().isBlank()) w.add("user_id = :user");
        if (f.keywords() != null && !f.keywords().isBlank()) w.add("LOWER(sql_summary) LIKE :kw");
        if (f.status() != null && !f.status().isEmpty()) w.add("status IN (:st)");
        if (f.from() != null) w.add("executed_at >= :fromIso");
        if (f.to() != null)   w.add("executed_at <  :toIso"); // 상한 제외
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

    // --------- DTO 매핑 (프로젝트 실제 QueryLogResponse 구조에 맞춤) ---------

    private QueryLogResponse toDto(QueryLog q) {
        // 1) 우선 순수 Map 으로 키를 맞춰 담는다(이름은 우리 엔티티 기준)
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("executedAt", q.getExecutedAt());
        m.put("userId",     q.getUserId());
        m.put("adminId",    q.getAdminId());
        m.put("sqlRaw",     q.getSqlRaw());
        m.put("sqlSummary", q.getSqlSummary());
        m.put("returnRows", q.getReturnRows());

        // status는 두 번 시도: enum 그대로 → 실패 시 문자열
        Object statusVal = q.getStatus(); // enum or null
        m.put("status", statusVal);

        try {
            return mapper.convertValue(m, QueryLogResponse.class);
        } catch (IllegalArgumentException e1) {
            // DTO가 문자열 status를 기대하는 경우 재시도
            m.put("status", statusVal == null ? null : q.getStatus().name());
            try {
                return mapper.convertValue(m, QueryLogResponse.class);
            } catch (IllegalArgumentException e2) {
                // 마지막 안전판: status 제거 후라도 변환
                m.remove("status");
                return mapper.convertValue(m, QueryLogResponse.class);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private LogSummaryResponse.UserBucket makeUserBucket(String key, long cnt) {
        try {
            Class<LogSummaryResponse.UserBucket> cls = LogSummaryResponse.UserBucket.class;
            for (var ctor : cls.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Object[] args = fillArgs(ctor.getParameterTypes(), key, cnt);
                return (LogSummaryResponse.UserBucket) ctor.newInstance(args);
            }
            throw new IllegalStateException("No constructor found for UserBucket");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to construct UserBucket reflectively", e);
        }
    }

    @SuppressWarnings("unchecked")
    private LogSummaryResponse.TimeBucket makeTimeBucket(String key, long cnt) {
        try {
            Class<LogSummaryResponse.TimeBucket> cls = LogSummaryResponse.TimeBucket.class;
            for (var ctor : cls.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Object[] args = fillArgs(ctor.getParameterTypes(), key, cnt);
                return (LogSummaryResponse.TimeBucket) ctor.newInstance(args);
            }
            throw new IllegalStateException("No constructor found for TimeBucket");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to construct TimeBucket reflectively", e);
        }
    }

    /**
     * 생성자 파라미터 타입 배열을 받아, 첫 번째 String에는 key, 첫 번째 숫자형에는 cnt를 주입.
     * 나머지는 0/false/"" 등의 안전한 기본값으로 채움.
     */
    private Object[] fillArgs(Class<?>[] paramTypes, String key, long cnt) {
        Object[] args = new Object[paramTypes.length];
        boolean keySet = false, cntSet = false;

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> t = paramTypes[i];

            if (!keySet && (t == String.class)) {
                args[i] = key;
                keySet = true;
                continue;
            }
            if (!cntSet && (t == long.class || t == Long.class || t == int.class || t == Integer.class
                    || t == double.class || t == Double.class)) {
                // 숫자형 첫 파라미터에 cnt 주입 (필요 시 캐스팅)
                if (t == long.class || t == Long.class)      args[i] = cnt;
                else if (t == int.class || t == Integer.class) args[i] = (int) cnt;
                else if (t == double.class || t == Double.class) args[i] = (double) cnt;
                cntSet = true;
                continue;
            }

            // 나머지는 타입별 기본값
            if (t == String.class) args[i] = "";
            else if (t == boolean.class || t == Boolean.class) args[i] = false;
            else if (t == long.class || t == Long.class) args[i] = 0L;
            else if (t == int.class || t == Integer.class) args[i] = 0;
            else if (t == double.class || t == Double.class) args[i] = 0.0d;
            else if (t.isEnum()) args[i] = t.getEnumConstants()[0]; // enum은 첫 상수로
            else args[i] = null;
        }
        return args;
    }
}
