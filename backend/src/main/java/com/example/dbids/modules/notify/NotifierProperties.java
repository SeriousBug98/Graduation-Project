package com.example.dbids.modules.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dbids.notifier")
public class NotifierProperties {

    /** LOW/MEDIUM/HIGH 등 정책에 쓸 임계치(선택) */
    private String severityThreshold;

    private Email email = new Email();
    private Slack slack = new Slack();

    public String getSeverityThreshold() { return severityThreshold; }
    public void setSeverityThreshold(String severityThreshold) { this.severityThreshold = severityThreshold; }
    public Email getEmail() { return email; }
    public void setEmail(Email email) { this.email = email; }
    public Slack getSlack() { return slack; }
    public void setSlack(Slack slack) { this.slack = slack; }

    // ---- Email ----
    public static class Email {
        private String host;
        private Integer port;
        private String from;
        private String toDefault;     // 폴백 수신자
        private String username;
        private String password;
        private boolean starttls = false;
        private boolean ssl = false;
        private boolean enabled = true;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getToDefault() { return toDefault; }
        public void setToDefault(String toDefault) { this.toDefault = toDefault; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isStarttls() { return starttls; }
        public void setStarttls(boolean starttls) { this.starttls = starttls; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    // ---- Slack ----
    public static class Slack {
        /** Incoming Webhook URL */
        private String webhookUrl;
        /** 전송 on/off */
        private boolean enabled;
        /** 메시지 앞에 멘션(예: @channel, @here) 넣고 싶으면 */
        private String mentionPrefix;
        /** HTTP 연결 타임아웃(ms) */
        private int timeoutMs = 4000;
        /** 유저명/아이콘 이모지(선택) */
        private String username;
        private String iconEmoji;

        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMentionPrefix() { return mentionPrefix; }
        public void setMentionPrefix(String mentionPrefix) { this.mentionPrefix = mentionPrefix; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getIconEmoji() { return iconEmoji; }
        public void setIconEmoji(String iconEmoji) { this.iconEmoji = iconEmoji; }
    }
}
