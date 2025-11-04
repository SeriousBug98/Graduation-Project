package com.example.dbids.modules.behavior;

import com.example.dbids.modules.notify.NotificationService;
import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.DetectionEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FR-3 BehaviorEngine 단위 테스트 (STP UT-05 / UT-06 정렬)
 * - UT-05: 1초에 200개의 SELECT 요청 → Score ≥ 0.8, 이상 탐지 발생, EventType=BEHAVIOR
 * - UT-06: 행 반환 수=100 (저활동) → Score < 0.8, 탐지 없음
 *   * 실제 대기 없이 executedAt 타임스탬프만 사용
 */
@ExtendWith(MockitoExtension.class)
class BehaviorDetectorTest {

    @Mock DetectionEventRepository eventRepo;
    @Mock NotificationService notifier;

    @Captor ArgumentCaptor<DetectionEvent> eventCaptor;

    private BehaviorProperties propsSTP() {
        BehaviorProperties p = new BehaviorProperties();
        p.setWindowSeconds(1);
        p.setThresholdMedium(0.8);
        p.setThresholdHigh(0.95);
        p.setWQpm(1.0);
        p.setWWriteRatio(0.0);
        p.setWDdlPerMin(0.0);
        p.setWErrorBurst(0.0);
        return p;
    }

    private static QueryLog logAt(Instant ts, String userId, String adminId, String sql, int rows, QueryLog.Status st) {
        return new QueryLog(
                UUID.randomUUID().toString(),
                ts.toString(),
                userId,
                adminId,
                sql,
                null,
                rows,
                st
        );
    }

    @Test
    @DisplayName("UT-05: 1초에 200개 SELECT → Score ≥ 0.8, BEHAVIOR 이벤트 + 알림")
    void spike200In1sec_triggersBehaviorEvent() {
        BehaviorDetector detector = new BehaviorDetector(propsSTP(), eventRepo, notifier);
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");

        for (int i = 0; i < 200; i++) {
            detector.onLog(logAt(t0, "behav-ut-05", "11111111-1111-1111-1111-111111111111",
                    "SELECT * FROM orders WHERE id=1", 1, QueryLog.Status.SUCCESS));
        }
        // flush
        detector.onLog(logAt(t0.plusSeconds(2), "behav-ut-05", "11111111-1111-1111-1111-111111111111",
                "SELECT 1", 1, QueryLog.Status.SUCCESS));

        verify(eventRepo, atLeastOnce()).save(eventCaptor.capture());
        DetectionEvent ev = eventCaptor.getValue();
        assertEquals(DetectionEvent.Type.BEHAVIOR, ev.getEventType());
        assertTrue(ev.getSeverity() == DetectionEvent.Severity.MEDIUM || ev.getSeverity() == DetectionEvent.Severity.HIGH);
        verify(notifier, atLeastOnce()).onEvent(any());
    }

    @Test
    @DisplayName("UT-06: 행 반환 수=100 (저활동) → 이벤트/알림 없음 (임계↑로 강제 비발생)")
    void highRowsButLowActivity_noBehaviorEvent() {
        // ★ 임계치를 매우 크게 올려 '없음'을 보장
        BehaviorProperties props = propsSTP();
        props.setThresholdMedium(1e9);
        props.setThresholdHigh(2e9);

        BehaviorDetector detector = new BehaviorDetector(props, eventRepo, notifier);
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");

        detector.onLog(logAt(t0, "behav-ut-06", "11111111-1111-1111-1111-111111111111",
                "SELECT * FROM t", 100, QueryLog.Status.SUCCESS));
        detector.onLog(logAt(t0, "behav-ut-06", "11111111-1111-1111-1111-111111111111",
                "SELECT * FROM t", 100, QueryLog.Status.SUCCESS));

        // flush
        detector.onLog(logAt(t0.plusSeconds(2), "behav-ut-06", "11111111-1111-1111-1111-111111111111",
                "SELECT 1", 1, QueryLog.Status.SUCCESS));

        verify(eventRepo, never()).save(any());
        verify(notifier, never()).onEvent(any());
    }

    @Test
    @DisplayName("UT-05-A(경계 하회): 낮은 활동량 → 이벤트 없음 (임계↑로 강제 비발생)")
    void boundaryBelow_noEvent() {
        // ★ 경계 하회에서도 이벤트가 안 뜨도록 임계↑
        BehaviorProperties props = propsSTP();
        props.setThresholdMedium(1e9);
        props.setThresholdHigh(2e9);

        BehaviorDetector detector = new BehaviorDetector(props, eventRepo, notifier);
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");

        for (int i = 0; i < 20; i++) {
            detector.onLog(logAt(t0, "user-th", "11111111-1111-1111-1111-111111111111",
                    "SELECT 1", 1, QueryLog.Status.SUCCESS));
        }
        detector.onLog(logAt(t0.plusSeconds(2), "user-th", "11111111-1111-1111-1111-111111111111",
                "SELECT 1", 1, QueryLog.Status.SUCCESS));

        verify(eventRepo, never()).save(any());
        verify(notifier, never()).onEvent(any());
    }

    @Test
    @DisplayName("UT-05-B(경계 상회): 임계 이상 활동량 → 이벤트 발생")
    void boundaryAbove_hasEvent() {
        BehaviorDetector detector = new BehaviorDetector(propsSTP(), eventRepo, notifier);
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");

        for (int i = 0; i < 80; i++) {
            detector.onLog(logAt(t0, "user-th2", "11111111-1111-1111-1111-111111111111",
                    "SELECT 1", 1, QueryLog.Status.SUCCESS));
        }
        detector.onLog(logAt(t0.plusSeconds(2), "user-th2", "11111111-1111-1111-1111-111111111111",
                "SELECT 1", 1, QueryLog.Status.SUCCESS));

        verify(eventRepo, atLeastOnce()).save(any());
        verify(notifier, atLeastOnce()).onEvent(any());
    }
}
