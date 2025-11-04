package com.example.dbids.api;

import com.example.dbids.dto.QueryLogDTO;
import com.example.dbids.modules.behavior.BehaviorDetector;
import com.example.dbids.modules.detection.DetectionService;
import com.example.dbids.modules.storage.StorageService;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import com.example.dbids.modules.authz.AuthZService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final StorageService storage;
    private final DetectionService detection;
    private final BehaviorDetector behavior;
    private final QueryLogRepository logRepo;
    private final AuthZService authz;  // FR-4

    public IngestController(StorageService storage,
            DetectionService detection,
            BehaviorDetector behavior,
            AuthZService authz,
            QueryLogRepository logRepo) {
        this.storage = storage;
        this.detection = detection;
        this.behavior = behavior;
        this.authz = authz;
        this.logRepo = logRepo;
    }

    @PostMapping("/log")
    public ResponseEntity<?> ingest(@RequestBody QueryLogDTO dto) {
        try {
            // FR-1: 저장 (UUID LogID 반환)
            String logId = storage.saveQueryLog(dto);

            // FR-2/FR-3: 저장된 엔티티 조회 후 탐지들 수행 (실패 시 삼킴: fail-open)
            try {
                QueryLog saved = logRepo.findById(logId).orElse(null);
                if (saved != null) {
                    detection.evaluateAndRecord(saved);
                    authz.evaluateAndRecord(saved);
                    behavior.onLog(saved);
                }
            } catch (Exception ignore) {}

            return ResponseEntity.created(URI.create("/logs/" + logId))
                    .body(Map.of("id", logId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.accepted().body(Map.of("error", "ingest deferred"));
        }
    }
}
