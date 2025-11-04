package com.example.dbids.modules.detection;

import com.example.dbids.modules.notify.NotificationService;
import com.example.dbids.modules.rule.SqlNormalizer;
import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.DetectionEventRepository;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DetectionServiceTest {

    @Mock DetectionEventRepository eventRepo;
    @Mock NotificationService notifier;
    @Mock QueryLogRepository logRepo;

    @Captor ArgumentCaptor<DetectionEvent> eventCaptor;

    private QueryLog log(String id, String sql, String userId, QueryLog.Status status) {
        return new QueryLog(
                id,
                "2025-01-01T00:00:00Z",
                userId,
                "11111111-1111-1111-1111-111111111111",
                sql,
                "sum",
                0,
                status
        );
    }

    @Test
    @DisplayName("UT-03-DS: DROP TABLE → PATTERN/HIGH 이벤트 + QueryLog.status=FAILURE 보정 + 알림 호출")
    void createsPatternHighOnDropTable_andMarksFailure_andNotifies() {
        // given
        DetectionService service = new DetectionService(eventRepo, notifier, logRepo);
        String logId = UUID.randomUUID().toString();
        // HIGH에서 보정이 일어나는지 보기 위해 초기 상태는 SUCCESS로 둔다
        QueryLog saved = log(logId, "/*comment*/ drop   table   students ;", "attacker", QueryLog.Status.SUCCESS);

        // save가 그대로 엔티티를 반환해도 DetectionService가 생성 시점에 UUID를 넣으므로 OK
        when(eventRepo.save(any(DetectionEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Optional<String> eventId = service.evaluateAndRecord(saved);

        // then
        assertTrue(eventId.isPresent(), "HIGH 패턴이면 이벤트 ID가 존재해야 한다");

        verify(eventRepo, times(1)).save(eventCaptor.capture());
        DetectionEvent ev = eventCaptor.getValue();

        assertEquals(DetectionEvent.Type.PATTERN, ev.getEventType());
        assertEquals(DetectionEvent.Severity.HIGH, ev.getSeverity());
        assertEquals(logId, ev.getLogId());

        String normalized = new SqlNormalizer().normalize(saved.getSqlRaw());
        assertEquals(normalized, ev.getSqlRaw());
        assertTrue(normalized.contains("DROP TABLE"));
        assertFalse(normalized.contains("/*")); // 코멘트 제거 확인

        // ★ HIGH → 상태 보정(FAILURE) & 저장 호출
        verify(logRepo, times(1)).save(saved);
        assertEquals(QueryLog.Status.FAILURE, saved.getStatus());

        // 알림 트리거 호출
        verify(notifier, times(1)).onEvent(any(DetectionEvent.class));
    }

    @Test
    @DisplayName("UT-03-DS: UNION SELECT → PATTERN/MEDIUM 이벤트(상태 보정 없음) + 알림 호출")
    void createsPatternMediumOnUnionSelect_andNoStatusChange_andNotifies() {
        // given
        DetectionService service = new DetectionService(eventRepo, notifier, logRepo);
        String logId = UUID.randomUUID().toString();
        QueryLog saved = log(logId, "SELECT a FROM t UNION    SELECT b FROM u", "x", QueryLog.Status.SUCCESS);

        when(eventRepo.save(any(DetectionEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Optional<String> eventId = service.evaluateAndRecord(saved);

        // then
        assertTrue(eventId.isPresent());

        verify(eventRepo, times(1)).save(eventCaptor.capture());
        DetectionEvent ev = eventCaptor.getValue();

        assertEquals(DetectionEvent.Type.PATTERN, ev.getEventType());
        assertEquals(DetectionEvent.Severity.MEDIUM, ev.getSeverity());
        assertEquals(logId, ev.getLogId());

        String normalized = ev.getSqlRaw();
        assertTrue(normalized.contains("UNION SELECT"));

        // MEDIUM은 상태 보정 없음
        verify(logRepo, never()).save(any());
        assertEquals(QueryLog.Status.SUCCESS, saved.getStatus());

        // 알림 트리거 호출
        verify(notifier, times(1)).onEvent(any(DetectionEvent.class));
    }

    @Test
    @DisplayName("UT-03-DS: OR 1=1 → PATTERN/MEDIUM 이벤트(정규화로 0=0) + 알림 호출")
    void createsPatternMediumOnOr1Eq1_andNotifies() {
        // given
        DetectionService service = new DetectionService(eventRepo, notifier, logRepo);
        String logId = UUID.randomUUID().toString();
        QueryLog saved = log(logId, "SELECT * FROM T WHERE name='abc' OR 1=1 -- bypass", "y", QueryLog.Status.SUCCESS);

        when(eventRepo.save(any(DetectionEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Optional<String> eventId = service.evaluateAndRecord(saved);

        // then
        assertTrue(eventId.isPresent());
        verify(eventRepo, times(1)).save(eventCaptor.capture());
        DetectionEvent ev = eventCaptor.getValue();

        assertEquals(DetectionEvent.Type.PATTERN, ev.getEventType());
        assertEquals(DetectionEvent.Severity.MEDIUM, ev.getSeverity());
        assertEquals(logId, ev.getLogId());

        String normalized = ev.getSqlRaw();
        assertTrue(
                normalized.contains("OR 0 = 0") || normalized.contains("OR 0=0"),
                "정규화 결과에 OR 0=0 패턴이 포함되어야 한다"
        );
        assertFalse(normalized.contains("--"));

        verify(logRepo, never()).save(any()); // 보정 없음
        verify(notifier, times(1)).onEvent(any(DetectionEvent.class));
    }

    @Test
    @DisplayName("UT-03-DS: SLEEP() → PATTERN/LOW 이벤트(상태 보정 없음) + 알림 호출")
    void createsPatternLowOnSleep_andNotifies() {
        // given
        DetectionService service = new DetectionService(eventRepo, notifier, logRepo);
        String logId = UUID.randomUUID().toString();
        QueryLog saved = log(logId, "SELECT * FROM users WHERE id=1 OR SLEEP(10)", "z", QueryLog.Status.SUCCESS);

        when(eventRepo.save(any(DetectionEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Optional<String> eventId = service.evaluateAndRecord(saved);

        // then
        assertTrue(eventId.isPresent());
        verify(eventRepo, times(1)).save(eventCaptor.capture());
        DetectionEvent ev = eventCaptor.getValue();

        assertEquals(DetectionEvent.Type.PATTERN, ev.getEventType());
        assertEquals(DetectionEvent.Severity.LOW, ev.getSeverity());
        assertEquals(logId, ev.getLogId());

        String normalized = ev.getSqlRaw();
        assertTrue(normalized.contains("SLEEP("));

        verify(logRepo, never()).save(any()); // 보정 없음
        verify(notifier, times(1)).onEvent(any(DetectionEvent.class));
    }

    @Test
    @DisplayName("UT-04-DS: 정상 SELECT → 이벤트 없음(저장/알림 호출 없음)")
    void noEventOnNormalSelect() {
        // given
        DetectionService service = new DetectionService(eventRepo, notifier, logRepo);
        String logId = UUID.randomUUID().toString();
        QueryLog saved = log(logId, "SELECT name FROM users;", "u", QueryLog.Status.SUCCESS);

        // when
        Optional<String> eventId = service.evaluateAndRecord(saved);

        // then
        assertTrue(eventId.isEmpty());
        verify(eventRepo, never()).save(any());
        verify(logRepo, never()).save(any());
        verify(notifier, never()).onEvent(any());
        assertEquals(QueryLog.Status.SUCCESS, saved.getStatus());
    }
}
