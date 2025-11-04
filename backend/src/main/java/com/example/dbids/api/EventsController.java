package com.example.dbids.api;

import com.example.dbids.dto.EventDtos;
import com.example.dbids.modules.events.EventQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventsController {

    private final EventQueryService svc;

    public EventsController(EventQueryService svc) {
        this.svc = svc;
    }

    /**
     * GET /api/events
     * 파라미터(선택): type, severity, from, to, user, adminId, q, page, size
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String adminId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            EventQueryService.SearchCond c = new EventQueryService.SearchCond();
            c.type = type; c.severity = severity; c.from = from; c.to = to;
            c.user = user; c.adminId = adminId; c.q = q; c.page = page; c.size = size;

            EventDtos.Page<EventDtos.EventSummary> result = svc.search(c);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            // enum 파싱 실패 등
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/events/{id}
     * 단건 상세(DetectionEvent + 연관된 QueryLog 스냅샷)
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable String id) {
        return svc.findDetail(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
