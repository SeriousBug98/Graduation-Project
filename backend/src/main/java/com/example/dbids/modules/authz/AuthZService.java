// src/main/java/com/example/dbids/modules/authz/AuthZService.java
package com.example.dbids.modules.authz;

import com.example.dbids.modules.notify.NotificationService;
import com.example.dbids.modules.rule.SqlNormalizer;
import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.DetectionEventRepository;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthZService {

    private final DetectionEventRepository eventRepo;
    private final AuthZEngine engine;
    private final SqlNormalizer normalizer = new SqlNormalizer();
    private final NotificationService notifier;
    private final QueryLogRepository logRepo;

    public AuthZService(DetectionEventRepository eventRepo,
            AuthZProperties props,
            NotificationService notifier,
            QueryLogRepository logRepo) {
        this.eventRepo = eventRepo;
        this.engine = new AuthZEngine(props);
        this.notifier = notifier;
        this.logRepo = logRepo;
    }

    @Transactional("sqliteTx")
    public Optional<String> evaluateAndRecord(QueryLog log) {
        try {
            // ★ userId 원본 그대로 전달
            var v = engine.evaluate(log.getUserId(), log.getSqlRaw());
            if (v.isEmpty()) return Optional.empty();

            var vio = v.get();
            String normalized = normalizer.normalize(log.getSqlRaw());

            DetectionEvent ev = new DetectionEvent(
                    UUID.randomUUID().toString(),
                    log.getId(),
                    DetectionEvent.Type.AUTHZ,
                    vio.severity(),
                    Instant.now().toString(),
                    normalized
            );
            eventRepo.save(ev);
            notifier.onEvent(ev);

            if (log.getStatus() != QueryLog.Status.FAILURE) {
                log.setStatus(QueryLog.Status.FAILURE);
                try { logRepo.save(log); } catch (Exception ignore) {}
            }
            return Optional.of(ev.getId());
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }
}
