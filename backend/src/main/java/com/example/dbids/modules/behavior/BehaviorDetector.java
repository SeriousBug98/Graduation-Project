package com.example.dbids.modules.behavior;

import com.example.dbids.modules.notify.NotificationService;
import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.DetectionEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SDS 준수: 추가 테이블 없이, 메모리 윈도우 집계 후
 * 임계치 초과 시 DetectionEvent(Type=BEHAVIOR)만 SQLite에 기록.
 */
@Service
public class BehaviorDetector {

    private final BehaviorProperties props;
    private final DetectionEventRepository eventRepo;
    private final NotificationService notifier;
    private final BehaviorFeatureExtractor extractor = new BehaviorFeatureExtractor();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public BehaviorDetector(BehaviorProperties props, DetectionEventRepository eventRepo, NotificationService notifier) {
        this.props = props;
        this.eventRepo = eventRepo;
        this.notifier = notifier;
    }

    /** 사용자별 윈도우 버킷(인메모리) */
    static class Bucket {
        long winStartEpoch; // 초 단위
        int q, write, ddl, err;
        String lastLogId;   // FK 대표 로그ID(가장 최근)
    }

    private long alignWindowStart(Instant ts) {
        long sec = ts.getEpochSecond();
        long w = props.getWindowSeconds();
        return (sec / w) * w;
    }

    private static Instant parseIso(String iso) { return Instant.parse(iso); }

    @Transactional("sqliteTx")
    public void onLog(QueryLog log) {
        try {
            Instant exec = parseIso(log.getExecutedAt());
            long start = alignWindowStart(exec);
            String user = log.getUserId();

            Bucket b = buckets.compute(user, (k, old) -> {
                if (old == null || old.winStartEpoch != start) {
                    if (old != null) flushAndMaybeAlert(user, old);
                    Bucket nb = new Bucket();
                    nb.winStartEpoch = start;
                    return nb;
                }
                return old;
            });

            var d = extractor.extract(log);
            b.q     += d.q;
            b.write += d.write;
            b.ddl   += d.ddl;
            b.err   += d.error;
            b.lastLogId = log.getId(); // FK 대표 연결

            // 별도 스케줄러 없이, 새 윈도우가 열릴 때 이전 버킷이 flush됨.
        } catch (Exception ignore) {
            // fail-open
        }
    }

    /** 윈도우 통계를 상수 임계 방식으로 평가 → 임계치 넘으면 BEHAVIOR 이벤트 기록 */
    private void flushAndMaybeAlert(String user, Bucket b) {
        try {
            double minutes    = props.getWindowSeconds() / 60.0;
            double qpm        = b.q / Math.max(minutes, 1e-6);
            double writeRatio = (b.q == 0) ? 0.0 : ((double) b.write / b.q);
            double ddlPerMin  = b.ddl / Math.max(minutes, 1e-6);
            double errBurst   = b.err;

            double score = props.getWQpm()        * qpm
                    + props.getWWriteRatio() * writeRatio
                    + props.getWDdlPerMin()  * ddlPerMin
                    + props.getWErrorBurst() * errBurst;

            DetectionEvent.Severity sev = null;
            if (score >= props.getThresholdHigh())       sev = DetectionEvent.Severity.HIGH;
            else if (score >= props.getThresholdMedium()) sev = DetectionEvent.Severity.MEDIUM;

            if (sev != null && b.lastLogId != null) {
                String snapshot = String.format(
                        "USER=%s QPM=%.2f WRITE_RATIO=%.2f DDL/m=%.2f ERR=%d SCORE=%.2f",
                        user, qpm, writeRatio, ddlPerMin, b.err, score
                );
                DetectionEvent ev = new DetectionEvent(
                        UUID.randomUUID().toString(),
                        b.lastLogId,                         // FK: 대표 로그ID
                        DetectionEvent.Type.BEHAVIOR,
                        sev,
                        Instant.now().toString(),
                        snapshot
                );
                eventRepo.save(ev);
                notifier.onEvent(ev);
            }
        } catch (Exception ignore) {
            // fail-open
        }
    }
}
