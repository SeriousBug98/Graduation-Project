package com.example.dbids.modules.notify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final NotifierProperties props;

    // spring.mail.username 이 설정되어 있으면 기본 from으로 쓰일 수 있음
    @Value("${spring.mail.username:}")
    private String springMailUsername;

    public EmailSender(JavaMailSender mailSender, NotifierProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    public void send(String subject, String body) {
        String to = props.getEmail().getToDefault();
        if (to == null || to.isBlank()) {
            throw new IllegalStateException("dbids.notifier.email.to-default is not set");
        }
        sendTo(subject, body, to);
    }

    public void sendTo(String subject, String body, String toAddress) {
        if (!props.getEmail().isEnabled()) return;

        String from = (props.getEmail().getFrom() != null && !props.getEmail().getFrom().isBlank())
                ? props.getEmail().getFrom()
                : springMailUsername; // 최후의 폴백

        SimpleMailMessage msg = new SimpleMailMessage();
        if (from != null && !from.isBlank()) msg.setFrom(from);
        msg.setTo(toAddress);
        msg.setSubject(subject);
        msg.setText(body);

        mailSender.send(msg);
    }
}
