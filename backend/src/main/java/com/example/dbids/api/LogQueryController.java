// src/main/java/com/example/dbids/api/LogQueryController.java
package com.example.dbids.api;

import com.example.dbids.dto.LogSummaryResponse;
import com.example.dbids.dto.QueryLogFilter;
import com.example.dbids.dto.QueryLogResponse;
import com.example.dbids.modules.storage.LogQueryService;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogQueryController {

    private final LogQueryService service;

    public LogQueryController(LogQueryService service) {
        this.service = service;
    }

    /** 전체 노출 + 필터/검색 */
    @GetMapping
    public Page<QueryLogResponse> list(
            @RequestParam(name="user", required=false) String user,
            @RequestParam(name="email", required=false) String emailAlias, // FE 호환
            @RequestParam(name="keywords", required=false) String keywords, // sqlSummary LIKE
            @RequestParam(name="status", required=false) List<String> status,
            @RequestParam(name="from", required=false) String from,   // ISO-8601 문자열
            @RequestParam(name="to", required=false) String to,
            @RequestParam(name="rowsMin", required=false) Integer rowsMin,
            @RequestParam(name="rowsMax", required=false) Integer rowsMax,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="50") int size,
            @RequestParam(defaultValue="executedAt,DESC") String sort
    ) {
        String effectiveUser = (user != null && !user.isBlank()) ? user : emailAlias; // email 호환
        Pageable pageable = PageRequest.of(Math.max(0, page), clamp(size, 1, 200), parseSort(sort));

        QueryLogFilter f = new QueryLogFilter(effectiveUser, keywords, status, from, to, rowsMin, rowsMax);
        return service.search(f, pageable);
    }

    /** 같은 조건으로 CSV 다운로드 */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(name="user", required=false) String user,
            @RequestParam(name="email", required=false) String emailAlias,
            @RequestParam(name="keywords", required=false) String keywords,
            @RequestParam(name="status", required=false) List<String> status,
            @RequestParam(name="from", required=false) String from,
            @RequestParam(name="to", required=false) String to,
            @RequestParam(name="rowsMin", required=false) Integer rowsMin,
            @RequestParam(name="rowsMax", required=false) Integer rowsMax,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="1000") int size,
            @RequestParam(defaultValue="executedAt,DESC") String sort
    ) {
        String effectiveUser = (user != null && !user.isBlank()) ? user : emailAlias;
        Pageable pageable = PageRequest.of(Math.max(0, page), clamp(size, 1, 10_000), parseSort(sort));

        QueryLogFilter f = new QueryLogFilter(effectiveUser, keywords, status, from, to, rowsMin, rowsMax);
        byte[] csv = service.exportCsv(f, pageable);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"query_logs.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/summary")
    public LogSummaryResponse summary(
            @RequestParam(name="user", required=false) String user,
            @RequestParam(name="email", required=false) String emailAlias, // FE 호환
            @RequestParam(name="keywords", required=false) String keywords,
            @RequestParam(name="status", required=false) List<String> status,
            @RequestParam(name="from", required=false) String from,
            @RequestParam(name="to", required=false) String to,
            @RequestParam(name="rowsMin", required=false) Integer rowsMin,
            @RequestParam(name="rowsMax", required=false) Integer rowsMax,
            @RequestParam(name="limit", required=false, defaultValue="50000") int limit  // 안전장치
    ) {
        String effectiveUser = (user != null && !user.isBlank()) ? user : emailAlias;
        QueryLogFilter f = new QueryLogFilter(effectiveUser, keywords, status, from, to, rowsMin, rowsMax);
        return service.summarize(f, limit);
    }

    // ===== helpers =====
    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Sort parseSort(String raw) {
        String v = (raw == null || raw.isBlank()) ? "executedAt,DESC" : raw.trim();
        String[] p = v.split(",", 2);
        String prop = p[0];
        Sort.Direction dir = (p.length > 1 && "ASC".equalsIgnoreCase(p[1])) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, prop);
    }
}
