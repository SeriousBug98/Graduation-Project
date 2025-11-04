package com.example.dbids.dto;

/** 알림 설정 DTO (admin_user만 갱신; alert_setting 테이블 없음) */
public class AlertSettingDTO {

    /** 하위호환용 – 서버는 무시함 */
    @Deprecated
    public String adminId;

    /** 이메일
     * - 키가 없으면: 미변경
     * - 키가 있으면: 빈문자 불가 (반드시 유효값)
     */
    public String email;

    /** Slack Webhook URL
     * - 키가 없으면: 미변경
     * - 빈 문자열("")이면: '유지'(변경하지 않음)
     * - 비어있지 않으면: 갱신 요청이지만 현재는 저장소가 없으므로 무시(유지)
     */
    public String slackWebhook;

    public AlertSettingDTO() {}
    public AlertSettingDTO(String email, String slackWebhook) {
        this.email = email;
        this.slackWebhook = slackWebhook;
    }

    /** 이메일 정규화: 키 없으면 null(미변경), 키 있으면 빈문자 불가 */
    public String normalizedEmailOrNull() {
        if (email == null) return null;
        String v = email.trim().toLowerCase();
        if (v.isEmpty()) return null;
        if (!v.contains("@")) throw new IllegalArgumentException("INVALID_EMAIL");
        return v;
    }

    /** Slack 정규화: null=미변경, ""=유지 신호, 그 외=트림 값(현재 저장은 안 함) */
    public String normalizedSlackOrNull() {
        if (slackWebhook == null) return null;
        String v = slackWebhook.trim();
        if (v.isEmpty()) return null; // 빈문자는 미변경
        if (!v.startsWith("https://hooks.slack.com/"))
            throw new IllegalArgumentException("INVALID_SLACK_WEBHOOK");
        return v;
    }
}
