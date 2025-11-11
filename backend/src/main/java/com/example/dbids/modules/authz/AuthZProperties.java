// src/main/java/com/example/dbids/modules/authz/AuthZProperties.java
package com.example.dbids.modules.authz;

import com.example.dbids.sqlite.model.DetectionEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Component
@ConfigurationProperties(prefix = "authz")
public class AuthZProperties {

    /** role name -> role definition */
    private Map<String, Role> roles;

    /** userId -> roleName (원본 그대로 저장) */
    private Map<String, String> userRoles;

    /** 케이스 무시 매칭용 (내부 전용) */
    private Map<String, String> userRolesCI;

    public Map<String, Role> getRoles() { return roles; }
    public void setRoles(Map<String, Role> roles) { this.roles = roles; }

    public Map<String, String> getUserRoles() { return userRoles; }
    public void setUserRoles(Map<String, String> userRoles) { this.userRoles = userRoles; }

    @PostConstruct
    public void init() {
        // 케이스 무시 맵 구성 (키만 UPPERCASE로 인덱싱) — 원본 userRoles는 그대로 보존
        userRolesCI = new LinkedHashMap<>();
        if (userRoles != null) {
            for (Map.Entry<String, String> e : userRoles.entrySet()) {
                if (e.getKey() == null) continue;
                userRolesCI.put(e.getKey().trim().toUpperCase(Locale.ROOT), e.getValue());
                // @ 빠진 설정도 있을 수 있어 fallback 키도 같이 인덱싱
                String noAt = e.getKey().replace("@", "");
                userRolesCI.put(noAt.trim().toUpperCase(Locale.ROOT), e.getValue());
            }
        }

        // 요약 로그
        System.out.println("[AUTHZ] === Properties Loaded ===");
        System.out.println("[AUTHZ] roles keys = " + (roles == null ? "[]" : roles.keySet()));
        System.out.println("[AUTHZ] userRoles(raw) = " + (userRoles == null ? "{}" : userRoles));
        System.out.println("[AUTHZ] userRoles(index, case-insensitive) size = " + (userRolesCI == null ? 0 : userRolesCI.size()));
        System.out.println("[AUTHZ] ==========================");
    }

    /** 입력 userId를 원본 그대로 두고, 내부적으로만 케이스 무시 + @무시 fallback 으로 roleName을 찾는다. */
    public String resolveRoleName(String userIdRaw) {
        if (userIdRaw == null || userIdRaw.isBlank() || userRolesCI == null) return null;
        String kUC = userIdRaw.trim().toUpperCase(Locale.ROOT);
        String v = userRolesCI.get(kUC);
        if (v != null) return v;
        String noAtUC = kUC.replace("@", "");
        return userRolesCI.get(noAtUC);
    }

    public static class Role {
        private List<String> allow;
        private List<String> deny;
        private DetectionEvent.Severity defaultSeverity = DetectionEvent.Severity.HIGH;

        public List<String> getAllow() { return allow; }
        public void setAllow(List<String> allow) { this.allow = allow; }
        public List<String> getDeny() { return deny; }
        public void setDeny(List<String> deny) { this.deny = deny; }
        public DetectionEvent.Severity getDefaultSeverity() { return defaultSeverity; }
        public void setDefaultSeverity(DetectionEvent.Severity defaultSeverity) { this.defaultSeverity = defaultSeverity; }
    }
}
