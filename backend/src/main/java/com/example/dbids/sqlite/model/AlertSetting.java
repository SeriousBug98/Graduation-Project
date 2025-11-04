// src/main/java/com/example/dbids/sqlite/model/AlertSetting.java
package com.example.dbids.sqlite.model;

import jakarta.persistence.*;

@Entity
@Table(name = "alert_setting")
public class AlertSetting {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;               // 항상 "default" 같은 단일 키

    @Column(name = "email", length = 255)
    private String email;            // 알림/로그인 이메일

    @Column(name = "slack_webhook", length = 1024)
    private String slackWebhook;     // Slack Webhook URL (null 허용)

    protected AlertSetting() { }

    public AlertSetting(String id, String email, String slackWebhook) {
        this.id = id;
        this.email = email;
        this.slackWebhook = slackWebhook;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getSlackWebhook() { return slackWebhook; }

    public void setEmail(String email) { this.email = email; }
    public void setSlackWebhook(String slackWebhook) { this.slackWebhook = slackWebhook; }
}
