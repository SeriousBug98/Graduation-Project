// src/main/java/com/example/dbids/modules/notify/NotificationService.java
package com.example.dbids.modules.notify;

import com.example.dbids.modules.auth.CurrentAdminEmailResolver;
import com.example.dbids.sqlite.model.DetectionEvent;
import com.example.dbids.sqlite.model.NotificationLog;
import com.example.dbids.sqlite.model.QueryLog;
import com.example.dbids.sqlite.repository.NotificationLogRepository;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final EmailSender emailSender;
    private final SlackSender slackSender;
    private final NotificationFormatter mailFmt;
    private final SlackFormatter slackFmt;
    private final CurrentAdminEmailResolver currentAdmin;
    private final QueryLogRepository logRepo;
    private final NotificationLogRepository notifRepo;
    private final NotifierProperties props;

    public NotificationService(EmailSender emailSender,
            SlackSender slackSender,
            NotificationFormatter mailFmt,
            SlackFormatter slackFmt,
            CurrentAdminEmailResolver currentAdmin,
            QueryLogRepository logRepo,
            NotificationLogRepository notifRepo,
            NotifierProperties props) {
        this.emailSender = emailSender;
        this.slackSender = slackSender;
        this.mailFmt = mailFmt;
        this.slackFmt = slackFmt;
        this.currentAdmin = currentAdmin;
        this.logRepo = logRepo;
        this.notifRepo = notifRepo;
        this.props = props;
    }

    /** sqliteTx로 묶어서 로그까지 한 트랜잭션으로 남김 */
    @Transactional("sqliteTx")
    public void onEvent(DetectionEvent ev) {
        Optional<QueryLog> logOpt = logRepo.findById(ev.getLogId());
        QueryLog log = logOpt.orElse(null);

        String now = Instant.now().toString();

        // ---- Email ----
        if (props.getEmail().isEnabled()) {
            String subject = mailFmt.emailSubject(ev, log);
            String body    = mailFmt.emailBody(ev, log);
            try {
                String to = currentAdmin.resolveNotifyEmailOrNull();
                if (to != null && !to.isBlank()) emailSender.sendTo(subject, body, to);
                else                               emailSender.send(subject, body);

                notifRepo.save(new NotificationLog(
                        java.util.UUID.randomUUID().toString(), ev.getId(),
                        NotificationLog.Channel.EMAIL, NotificationLog.Status.SENT,
                        null, null, now   // error_code, error_message, sent_at
                ));
            } catch (Exception e) {
                notifRepo.save(new NotificationLog(
                        java.util.UUID.randomUUID().toString(), ev.getId(),
                        NotificationLog.Channel.EMAIL, NotificationLog.Status.FAILED,
                        e.getClass().getSimpleName(), shortMsg(e), now
                ));
            }
        }

        // ---- Slack ----
        if (props.getSlack().isEnabled()) {
            String text = slackFmt.text(ev, log);
            try {
                if (text != null && !text.isBlank()) slackSender.send(text);
                notifRepo.save(new NotificationLog(
                        java.util.UUID.randomUUID().toString(), ev.getId(),
                        NotificationLog.Channel.SLACK, NotificationLog.Status.SENT,
                        null, null, now
                ));
            } catch (Exception e) {
                notifRepo.save(new NotificationLog(
                        java.util.UUID.randomUUID().toString(), ev.getId(),
                        NotificationLog.Channel.SLACK, NotificationLog.Status.FAILED,
                        e.getClass().getSimpleName(), shortMsg(e), now
                ));
            }
        }
    }

    private String shortMsg(Throwable e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return (m.length() > 480) ? m.substring(0, 477) + "..." : m;
    }
}
