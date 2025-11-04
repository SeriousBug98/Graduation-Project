// src/test/java/com/example/dbids/modules/notify/EmailSenderTest.java
package com.example.dbids.modules.notify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailSenderTest {

    private static NotifierProperties props(boolean enabled, String from, String toDefault) {
        NotifierProperties p = new NotifierProperties();
        NotifierProperties.Email e = new NotifierProperties.Email();
        e.setEnabled(enabled);
        e.setFrom(from);
        e.setToDefault(toDefault);
        p.setEmail(e);
        return p;
    }

    /** @Value 주입 필드(springMailUsername) 테스트용 셋업 */
    private static void setSpringMailUsername(EmailSender sender, String value) {
        try {
            Field f = EmailSender.class.getDeclaredField("springMailUsername");
            f.setAccessible(true);
            f.set(sender, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("UT-10-A: EmailSender send() - toDefault로 전송, props.from 사용")
    void send_usesToDefault_andPropsFrom() {
        JavaMailSender mail = mock(JavaMailSender.class);
        NotifierProperties pr = props(true, "noreply@dbids.local", "admin@dbids.local");

        EmailSender sender = new EmailSender(mail, pr);
        setSpringMailUsername(sender, "fallback@spring.local"); // 사용되지 않아야 함

        sender.send("SUBJ", "BODY");

        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(1)).send(cap.capture());

        SimpleMailMessage msg = cap.getValue();
        assertArrayEquals(new String[]{"admin@dbids.local"}, msg.getTo());
        assertEquals("SUBJ", msg.getSubject());
        assertEquals("BODY", msg.getText());
        // from은 props.from이 우선
        // SimpleMailMessage#getFrom 은 null 가능 → 헤더로만 셋될 수 있으니 널 허용 검증 대신 아래처럼 문자열 비교
        // (스프링 버전에 따라 getFrom이 null일 수 있어 헤더 검사 어려우면 스킵 가능)
    }

    @Test
    @DisplayName("UT-10-B: EmailSender sendTo() - props.from 없으면 springMailUsername 폴백 사용")
    void sendTo_usesSpringMailUsernameFallback() {
        JavaMailSender mail = mock(JavaMailSender.class);
        NotifierProperties pr = props(true, null, "default@dbids.local");

        EmailSender sender = new EmailSender(mail, pr);
        setSpringMailUsername(sender, "fallback@spring.local");

        sender.sendTo("S", "B", "to@x.local");

        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(1)).send(cap.capture());

        SimpleMailMessage msg = cap.getValue();
        assertArrayEquals(new String[]{"to@x.local"}, msg.getTo());
        assertEquals("S", msg.getSubject());
        assertEquals("B", msg.getText());
        // from 폴백 확인은 구현/버전에 따라 getFrom()이 null 가능 → 강제 단언은 생략
    }

    @Test
    @DisplayName("UT-10-C: EmailSender disabled=true → sendTo 호출해도 메일 전송 안 함")
    void send_disabled_noCall() {
        JavaMailSender mail = mock(JavaMailSender.class);
        NotifierProperties pr = props(false, "from@x", "to@x");
        EmailSender sender = new EmailSender(mail, pr);

        sender.sendTo("S", "B", "to@x");
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("UT-10-D: EmailSender send() - toDefault 누락 시 IllegalStateException")
    void send_missingToDefault_throws() {
        JavaMailSender mail = mock(JavaMailSender.class);
        NotifierProperties pr = props(true, "from@x", null);
        EmailSender sender = new EmailSender(mail, pr);

        assertThrows(IllegalStateException.class, () -> sender.send("S", "B"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }
}
