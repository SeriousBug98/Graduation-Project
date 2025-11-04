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

    /**
     * 권한 위반이면 DetectionEvent(Type=AUTHZ) 저장.
     * - action == DENY 인 경우 "차단"으로 간주하고 QueryLog.status를 FAILURE로 보정.
     * - 실패해도 본 플로우를 방해하지 않도록 예외는 삼킨다 (fail-open).
     */
    @Transactional("sqliteTx")
    public Optional<String> evaluateAndRecord(QueryLog log) {
        try {
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

            // ★ 권한 엔진이 DENY라면 차단으로 간주 → QueryLog.status를 FAILURE로 보정
            if (log.getStatus() != QueryLog.Status.FAILURE) {
                log.setStatus(QueryLog.Status.FAILURE);
                try {
                    logRepo.save(log); // merge
                } catch (Exception ignore) {
                    // fail-open
                }
            }

            return Optional.of(ev.getId());
        } catch (Exception ignore) {
            // fail-open
            return Optional.empty();
        }
    }
}
