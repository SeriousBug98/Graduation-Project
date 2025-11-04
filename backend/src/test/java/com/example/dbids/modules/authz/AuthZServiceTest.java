package com.example.dbids.modules.authz;

import com.example.dbids.modules.notify.NotificationService;
import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.DetectionEventRepository;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthZServiceTest {

    @Mock DetectionEventRepository eventRepo;
    @Mock NotificationService notifier;
    @Mock QueryLogRepository logRepo;

    @Captor ArgumentCaptor<DetectionEvent> eventCaptor;

    private static QueryLog log(String sql, String userId, QueryLog.Status st) {
        return new QueryLog(
                UUID.randomUUID().toString(),
                Instant.parse("2025-01-01T00:00:00Z").toString(),
                userId,
                "11111111-1111-1111-1111-111111111111",
                sql,
                "summary",
                0,
                st
        );
    }

    /** READ_ONLY: allow SELECT:*, deny INSERT/UPDATE/DELETE/DDL:*  /  DBA: allow all */
    private static AuthZProperties propsStd() {
        AuthZProperties p = new AuthZProperties();
        AuthZProperties.Role ro = new AuthZProperties.Role();
        ro.setAllow(List.of("SELECT:*"));
        ro.setDeny(List.of("INSERT:*","UPDATE:*","DELETE:*","DDL:*"));
        ro.setDefaultSeverity(DetectionEvent.Severity.HIGH);

        AuthZProperties.Role dba = new AuthZProperties.Role();
        dba.setAllow(List.of("SELECT:*","INSERT:*","UPDATE:*","DELETE:*","DDL:*"));
        dba.setDeny(List.of());

        p.setRoles(Map.of(
                "READ_ONLY", ro,
                "DBA", dba
        ));
        p.setUserRoles(Map.of(
                "ro-user", "READ_ONLY",
                "dba-user", "DBA"
        ));
        return p;
    }

    @Test
    @DisplayName("UT-07: READ_ONLY 사용자의 DELETE → AUTHZ 이벤트 생성 + 상태보정(FAILURE) + 알림")
    void readOnlyDelete_producesAuthzEvent_andMarksFailure() {
        // given
        AuthZService svc = new AuthZService(eventRepo, propsStd(), notifier, logRepo);
        QueryLog q = log("DELETE FROM orders;", "ro-user", QueryLog.Status.SUCCESS);

        when(eventRepo.save(any(DetectionEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Optional<String> evId = svc.evaluateAndRecord(q);

        // then
        assertTrue(evId.isPresent());
        verify(eventRepo, times(1)).save(eventCaptor.capture());
        DetectionEvent ev = eventCaptor.getValue();

        assertEquals(DetectionEvent.Type.AUTHZ, ev.getEventType());
        assertEquals(q.getId(), ev.getLogId());
        // 정책상 기본 severity는 HIGH
        assertEquals(DetectionEvent.Severity.HIGH, ev.getSeverity());
        assertTrue(ev.getSqlRaw().toUpperCase().contains("DENY")
                        || ev.getSqlRaw().toUpperCase().contains("AUTHZ"),
                "sqlRaw(스냅샷 텍스트)에 권한 위반 정보가 포함되어야 함");

        // ★ DENY → 상태 보정(FAILURE) 및 저장 시도
        assertEquals(QueryLog.Status.FAILURE, q.getStatus());
        verify(logRepo, times(1)).save(q);

        // 알림 호출
        verify(notifier, times(1)).onEvent(any(DetectionEvent.class));
    }

    @Test
    @DisplayName("UT-08: DBA 사용자의 DELETE → 이벤트 없음, 상태보정/알림/저장 호출 없음")
    void dbaDelete_allows_noEvent() {
        // given
        AuthZService svc = new AuthZService(eventRepo, propsStd(), notifier, logRepo);
        QueryLog q = log("DELETE FROM orders;", "dba-user", QueryLog.Status.SUCCESS);

        // when
        Optional<String> evId = svc.evaluateAndRecord(q);

        // then
        assertTrue(evId.isEmpty());
        assertEquals(QueryLog.Status.SUCCESS, q.getStatus());
        verify(eventRepo, never()).save(any());
        verify(logRepo, never()).save(any());
        verify(notifier, never()).onEvent(any());
    }

    @Test
    @DisplayName("UT-08-A: userRole 미지정 → 정책 비적용(허용) → 이벤트 없음")
    void noRole_assumedAllowed() {
        AuthZProperties p = propsStd();
        p.setUserRoles(Map.of()); // 비움
        AuthZService svc = new AuthZService(eventRepo, p, notifier, logRepo);
        QueryLog q = log("DELETE FROM orders;", "unknown-user", QueryLog.Status.SUCCESS);

        Optional<String> evId = svc.evaluateAndRecord(q);

        assertTrue(evId.isEmpty());
        verify(eventRepo, never()).save(any());
        verify(notifier, never()).onEvent(any());
    }

    @Test
    @DisplayName("UT-08-B: fail-open — 저장 중 예외 발생 시 Optional.empty 반환, 흐름 차단 없음")
    void failOpen_whenSavingEventThrows() {
        AuthZService svc = new AuthZService(eventRepo, propsStd(), notifier, logRepo);
        QueryLog q = log("DELETE FROM orders;", "ro-user", QueryLog.Status.SUCCESS);

        when(eventRepo.save(any())).thenThrow(new RuntimeException("disk full"));

        Optional<String> evId = svc.evaluateAndRecord(q);

        assertTrue(evId.isEmpty(), "예외가 나도 fail-open으로 empty 반환");
        verify(notifier, never()).onEvent(any());
        // eventRepo 단계에서 터져서 상태 보정도 시도되지 않음
        verify(logRepo, never()).save(any());
    }
}
