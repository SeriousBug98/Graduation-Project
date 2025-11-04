package com.example.dbids.modules.notify;

import com.example.dbids.modules.auth.CurrentAdminEmailResolver;
import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.NotificationLog;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.NotificationLogRepository;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * STP FR-5 Notification 단위 테스트
 * - UT-09: Slack/Email 실시간 알림
 * - UT-10: 성공 시 NotificationLog(Status=SENT) 저장
 * 추가: 실패 시 FAILED 기록도 커버
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock EmailSender emailSender;
    @Mock SlackSender slackSender;
    @Mock NotificationFormatter mailFmt;
    @Mock SlackFormatter slackFmt;
    @Mock
    CurrentAdminEmailResolver currentAdmin;
    @Mock QueryLogRepository logRepo;
    @Mock NotificationLogRepository notifRepo;

    @Captor ArgumentCaptor<NotificationLog> notifCaptor;

    // ---------- 공통 헬퍼 ----------
    private static QueryLog sampleLog(String logId) {
        return new QueryLog(
                logId,
                "2025-01-01T00:00:00Z",
                "tester",
                "11111111-1111-1111-1111-111111111111",
                "SELECT * FROM users;",
                "users 전체 조회",
                3,
                QueryLog.Status.SUCCESS
        );
    }

    private static DetectionEvent sampleEvent(String eventId, String logId, DetectionEvent.Severity sev) {
        return new DetectionEvent(
                eventId,
                logId,
                DetectionEvent.Type.PATTERN,
                sev,
                Instant.now().toString(),
                "SELECT * FROM users;"
        );
    }

    // 이메일만 ON
    private static NotifierProperties emailOnlyProps() {
        var props = new NotifierProperties();

        var email = new NotifierProperties.Email();
        email.setEnabled(true);
        email.setFrom("noreply@dbids.local");
        email.setToDefault("admin@dbids.local");
        props.setEmail(email);

        var slack = new NotifierProperties.Slack();
        slack.setEnabled(false);
        props.setSlack(slack);

        return props;
    }

    // 슬랙만 ON
    private static NotifierProperties slackOnlyProps() {
        var props = new NotifierProperties();

        var email = new NotifierProperties.Email();
        email.setEnabled(false);
        email.setFrom("noreply@dbids.local");
        email.setToDefault("admin@dbids.local");
        props.setEmail(email);

        var slack = new NotifierProperties.Slack();
        slack.setEnabled(true);
        slack.setWebhookUrl("https://hooks.slack.test/xxx"); // 더미 URL (실제 호출은 mock)
        props.setSlack(slack);

        return props;
    }

    // 둘 다 ON
    private static NotifierProperties bothOnProps() {
        var props = new NotifierProperties();

        var email = new NotifierProperties.Email();
        email.setEnabled(true);
        email.setFrom("noreply@dbids.local");
        email.setToDefault("admin@dbids.local");
        props.setEmail(email);

        var slack = new NotifierProperties.Slack();
        slack.setEnabled(true);
        slack.setWebhookUrl("https://hooks.slack.test/xxx");
        props.setSlack(slack);

        return props;
    }

    // =============== TC 1: 이메일 전용 ===============
    @Test
    @DisplayName("UT-10-A: Email ON, Slack OFF → EMAIL/SENT 로그 1건 저장")
    void email_only_savesEmailSent() {
        var props = emailOnlyProps();
        var svc = new NotificationService(
                emailSender, slackSender, mailFmt, slackFmt, currentAdmin, logRepo, notifRepo, props
        );

        String logId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        when(logRepo.findById(logId)).thenReturn(Optional.of(sampleLog(logId)));
        when(currentAdmin.resolveNotifyEmailOrNull()).thenReturn(null); // → EmailSender.send(subject, body) 경로
        when(mailFmt.emailSubject(any(), any())).thenReturn("[DB-IDS] [MEDIUM] Type=PATTERN User=tester");
        when(mailFmt.emailBody(any(), any())).thenReturn("body");

        var ev = sampleEvent(eventId, logId, DetectionEvent.Severity.MEDIUM);

        // when
        svc.onEvent(ev);

        // then
        verify(emailSender, times(1)).send(anyString(), anyString());
        verify(emailSender, never()).sendTo(anyString(), anyString(), anyString());
        verify(slackSender, never()).send(anyString()); // 꺼져 있으므로 호출 X

        verify(notifRepo, times(1)).save(notifCaptor.capture());
        NotificationLog saved = notifCaptor.getValue();
        assertEquals(NotificationLog.Channel.EMAIL, saved.getChannel());
        assertEquals(NotificationLog.Status.SENT, saved.getStatus());
        assertEquals(eventId, saved.getEventId());
        assertNotNull(saved.getSentAt());
    }

    // =============== TC 2: 슬랙 전용 ===============
    @Test
    @DisplayName("UT-09-A: Slack ON, Email OFF → SLACK/SENT 로그 1건 저장")
    void slack_only_savesSlackSent() {
        var props = slackOnlyProps();
        var svc = new NotificationService(
                emailSender, slackSender, mailFmt, slackFmt, currentAdmin, logRepo, notifRepo, props
        );

        String logId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        when(logRepo.findById(logId)).thenReturn(Optional.of(sampleLog(logId)));
        when(slackFmt.text(any(), any())).thenReturn("*DB-IDS* alert");

        var ev = sampleEvent(eventId, logId, DetectionEvent.Severity.HIGH);

        // when
        svc.onEvent(ev);

        // then
        verify(slackSender, times(1)).send(anyString());
        verify(emailSender, never()).send(anyString(), anyString());
        verify(emailSender, never()).sendTo(anyString(), anyString(), anyString());

        verify(notifRepo, times(1)).save(notifCaptor.capture());
        NotificationLog saved = notifCaptor.getValue();
        assertEquals(NotificationLog.Channel.SLACK, saved.getChannel());
        assertEquals(NotificationLog.Status.SENT, saved.getStatus());
        assertEquals(eventId, saved.getEventId());
        assertNotNull(saved.getSentAt());
    }

    // =============== TC 3: 둘 다 ON (순서 무관, 둘 다 기록) ===============
    @Test
    @DisplayName("UT-09-B/UT-10-B: Email+Slack ON → EMAIL/SENT + SLACK/SENT 각 1건씩 저장")
    void both_on_savesBothChannels() {
        var props = bothOnProps();
        var svc = new NotificationService(
                emailSender, slackSender, mailFmt, slackFmt, currentAdmin, logRepo, notifRepo, props
        );

        String logId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        when(logRepo.findById(logId)).thenReturn(Optional.of(sampleLog(logId)));
        when(currentAdmin.resolveNotifyEmailOrNull()).thenReturn(null); // default toDefault 경로 사용
        when(mailFmt.emailSubject(any(), any())).thenReturn("[DB-IDS] [HIGH] Type=PATTERN User=tester");
        when(mailFmt.emailBody(any(), any())).thenReturn("body");
        when(slackFmt.text(any(), any())).thenReturn("*DB-IDS* alert");

        var ev = sampleEvent(eventId, logId, DetectionEvent.Severity.HIGH);

        // when
        svc.onEvent(ev);

        // then: 발송 호출
        verify(emailSender, times(1)).send(anyString(), anyString());
        verify(slackSender, times(1)).send(anyString());

        // 로그는 2건 저장 (순서는 구현에 따라 EMAIL → SLACK 이지만, 순서 단언은 하지 않음)
        verify(notifRepo, times(2)).save(notifCaptor.capture());
        List<NotificationLog> logs = notifCaptor.getAllValues();

        boolean hasEmailSent = logs.stream().anyMatch(l ->
                l.getChannel() == NotificationLog.Channel.EMAIL &&
                        l.getStatus() == NotificationLog.Status.SENT &&
                        eventId.equals(l.getEventId())
        );
        boolean hasSlackSent = logs.stream().anyMatch(l ->
                l.getChannel() == NotificationLog.Channel.SLACK &&
                        l.getStatus() == NotificationLog.Status.SENT &&
                        eventId.equals(l.getEventId())
        );

        assertTrue(hasEmailSent, "EMAIL/SENT 로그가 1건 이상 있어야 함");
        assertTrue(hasSlackSent, "SLACK/SENT 로그가 1건 이상 있어야 함");
    }

    // =============== TC 4: 이메일 실패 → EMAIL/FAILED 저장 (슬랙 OFF로 단순화) ===============
    @Test
    @DisplayName("UT-10-C: Email 전송 실패 → EMAIL/FAILED 로그 저장 (예외 삼키고 계속 진행)")
    void email_failure_logsFailed() {
        var props = emailOnlyProps();
        var svc = new NotificationService(
                emailSender, slackSender, mailFmt, slackFmt, currentAdmin, logRepo, notifRepo, props
        );

        String logId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        when(logRepo.findById(logId)).thenReturn(Optional.of(sampleLog(logId)));
        when(mailFmt.emailSubject(any(), any())).thenReturn("[DB-IDS] [MEDIUM] Type=PATTERN User=tester");
        when(mailFmt.emailBody(any(), any())).thenReturn("body");
        when(currentAdmin.resolveNotifyEmailOrNull()).thenReturn("owner@dbids.local"); // sendTo 경로로 보냄

        // EmailSender에서 예외 발생
        doThrow(new RuntimeException("SMTP_FAIL")).when(emailSender)
                .sendTo(anyString(), anyString(), anyString());

        var ev = sampleEvent(eventId, logId, DetectionEvent.Severity.MEDIUM);

        // when
        svc.onEvent(ev);

        // then: 실패 시 FAILED 로그 남김
        verify(notifRepo, times(1)).save(notifCaptor.capture());
        NotificationLog saved = notifCaptor.getValue();
        assertEquals(NotificationLog.Channel.EMAIL, saved.getChannel());
        assertEquals(NotificationLog.Status.FAILED, saved.getStatus());
        assertEquals(eventId, saved.getEventId());
        assertNotNull(saved.getErrorCode());
        assertNotNull(saved.getErrorMessage());
    }

    // =============== TC 5: 슬랙 실패 → SLACK/FAILED 저장 (이메일 OFF로 단순화) ===============
    @Test
    @DisplayName("UT-10-D: Slack 전송 실패 → SLACK/FAILED 로그 저장 (예외 삼키고 계속 진행)")
    void slack_failure_logsFailed() {
        var props = slackOnlyProps();
        var svc = new NotificationService(
                emailSender, slackSender, mailFmt, slackFmt, currentAdmin, logRepo, notifRepo, props
        );

        String logId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        when(logRepo.findById(logId)).thenReturn(Optional.of(sampleLog(logId)));
        when(slackFmt.text(any(), any())).thenReturn("*DB-IDS* alert");

        // SlackSender에서 예외 발생
        doThrow(new RuntimeException("SLACK_WEBHOOK_HTTP_500")).when(slackSender).send(anyString());

        var ev = sampleEvent(eventId, logId, DetectionEvent.Severity.HIGH);

        // when
        svc.onEvent(ev);

        // then: FAILED 저장
        verify(notifRepo, times(1)).save(notifCaptor.capture());
        NotificationLog saved = notifCaptor.getValue();
        assertEquals(NotificationLog.Channel.SLACK, saved.getChannel());
        assertEquals(NotificationLog.Status.FAILED, saved.getStatus());
        assertEquals(eventId, saved.getEventId());
        assertNotNull(saved.getErrorCode());
        assertNotNull(saved.getErrorMessage());
    }
}
