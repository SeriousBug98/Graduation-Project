package com.example.dbids.modules.authz;

import com.example.dbids.sqlite.model.DetectionEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "authz")
public class AuthZProperties {

    /**
     * roles:
     *   READ_ONLY:
     *     allow: ["SELECT:*"]
     *     deny:  ["INSERT:*","UPDATE:*","DELETE:*","DDL:*"]
     *     defaultSeverity: HIGH
     *   DBA:
     *     allow: ["SELECT:*","INSERT:*","UPDATE:*","DELETE:*","DDL:*"]
     *     deny:  []
     *     defaultSeverity: HIGH
     */
    private Map<String, Role> roles;

    /** 사용자 → 롤 매핑 (userId 기준) */
    private Map<String, String> userRoles;

    public Map<String, Role> getRoles() { return roles; }
    public void setRoles(Map<String, Role> roles) { this.roles = roles; }
    public Map<String, String> getUserRoles() { return userRoles; }
    public void setUserRoles(Map<String, String> userRoles) { this.userRoles = userRoles; }

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
