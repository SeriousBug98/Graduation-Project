package com.example.dbids.sqlite.model;

import jakarta.persistence.*;

@Entity
@Table(name = "admin_user", indexes = {
        @Index(name = "uk_admin_email", columnList = "email", unique = true)
})
public class AdminUser {

    public enum Role { READER, WRITER, DBA }

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;                     // UUID

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "slack_webhook")
    private String slackWebhook;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;           // SHA-256 hex

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Column(name = "last_login")
    private String lastLogin;              // ISO-8601 문자열

    protected AdminUser() {} // for JPA

    public AdminUser(String id, String email, String passwordHash, Role role, String lastLogin) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.lastLogin = lastLogin;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getSlackWebhook() { return slackWebhook; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getLastLogin() { return lastLogin; }

    public void setLastLogin(String lastLogin) { this.lastLogin = lastLogin; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setRole(Role role) { this.role = role; }
    public void setId(String id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setSlackWebhook(String slackWebhook) { this.slackWebhook = slackWebhook;}
}
