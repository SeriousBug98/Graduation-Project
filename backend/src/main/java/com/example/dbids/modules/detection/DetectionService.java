package com.example.dbids.modules.detection;

import com.example.dbids.modules.notify.NotificationService;
import com.example.dbids.modules.rule.RuleEngine;
import com.example.dbids.modules.rule.RuleSetProvider;
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
public class DetectionService {

    private final DetectionEventRepository eventRepo;
    private final SqlNormalizer normalizer;
    private final RuleEngine engine;
    private final NotificationService notifier;
    private final QueryLogRepository logRepo;

    public DetectionService(DetectionEventRepository eventRepo,
            NotificationService notifier,
            QueryLogRepository logRepo) {
        this.eventRepo = eventRepo;
        this.normalizer = new SqlNormalizer();
        this.engine = new RuleEngine(RuleSetProvider.defaultRules());
        this.notifier = notifier;
        this.logRepo = logRepo;
    }

    /**
     * FR-2: 저장된 QueryLog를 받아 정규화 → 룰 평가 → 이벤트 기록.
     * - 패턴 탐지 결과가 HIGH면 "차단"으로 간주하여 해당 로그의 status를 FAILURE로 보정.
     * - 실패해도 본 플로우를 방해하지 않도록 예외는 삼킨다 (fail-open).
     */
    @Transactional("sqliteTx")
    public Optional<String> evaluateAndRecord(QueryLog savedLog) {
        try {
            String normalized = normalizer.normalize(savedLog.getSqlRaw());
            Optional<RuleEngine.Match> m = engine.evaluate(normalized);
            if (m.isEmpty()) return Optional.empty();

            RuleEngine.Match match = m.get();

            DetectionEvent ev = new DetectionEvent(
                    UUID.randomUUID().toString(),      // EventID(UUID)
                    savedLog.getId(),                  // LogID(UUID)
                    DetectionEvent.Type.PATTERN,       // FR-2는 PATTERN
                    match.severity,                    // Severity
                    Instant.now().toString(),          // OccurredAt (ISO-8601)
                    normalized                         // SQLRaw(정규화/마스킹)
            );
            String eventId = eventRepo.save(ev).getId();
            notifier.onEvent(ev);

            // ★ 패턴 탐지 HIGH = 차단으로 간주 → QueryLog.status를 FAILURE로 보정
            if (match.severity == DetectionEvent.Severity.HIGH
                    && savedLog.getStatus() != QueryLog.Status.FAILURE) {
                savedLog.setStatus(QueryLog.Status.FAILURE);
                try {
                    logRepo.save(savedLog); // merge
                } catch (Exception ignore) {
                    // fail-open: 상태 보정 실패하더라도 흐름 중단하지 않음
                }
            }

            return Optional.of(eventId);
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }
}
