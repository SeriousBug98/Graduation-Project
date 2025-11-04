package com.example.dbids.modules.authz;

import com.example.dbids.sqlite.model.DetectionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthZEngineTest {

    private static AuthZEngine engineStd() {
        AuthZProperties p = new AuthZProperties();
        AuthZProperties.Role ro = new AuthZProperties.Role();
        ro.setAllow(List.of("SELECT:*"));
        ro.setDeny(List.of("DELETE:*")); // 간단화
        ro.setDefaultSeverity(DetectionEvent.Severity.HIGH);

        AuthZProperties.Role dba = new AuthZProperties.Role();
        dba.setAllow(List.of("SELECT:*","INSERT:*","UPDATE:*","DELETE:*","DDL:*"));
        dba.setDeny(List.of());

        p.setRoles(Map.of("READ_ONLY", ro, "DBA", dba));
        p.setUserRoles(Map.of("ro-user", "READ_ONLY", "dba-user", "DBA"));

        return new AuthZEngine(p);
    }

    @Test
    @DisplayName("UT-07-A: READ_ONLY + DELETE orders → Violation 반환, ruleMatched=DENY DELETE:*")
    void readOnlyDelete_denied() {
        AuthZEngine eng = engineStd();
        Optional<AuthZEngine.Violation> v = eng.evaluate("ro-user", "DELETE FROM orders;");
        assertTrue(v.isPresent());
        assertEquals(AuthZEngine.Action.DELETE, v.get().action());
        assertTrue(v.get().ruleMatched().toUpperCase().contains("DENY DELETE"),
                "DENY 규칙 텍스트를 포함해야 함");
    }

    @Test
    @DisplayName("UT-08-C: DBA + DELETE → Violation 없음")
    void dbaDelete_allowed() {
        AuthZEngine eng = engineStd();
        Optional<AuthZEngine.Violation> v = eng.evaluate("dba-user", "DELETE FROM orders;");
        assertTrue(v.isEmpty());
    }

    @Test
    @DisplayName("UT-07-B: 테이블 파싱 실패 시 *로 평가해도 규칙 매칭(DENY *) 동작")
    void starFallback_whenTableUnknown() {
        // DENY DELETE:* 이므로 테이블을 못 잡아도 차단되어야 함
        AuthZEngine eng = engineStd();
        // 비정형 SQL (테이블 추정 실패 가능)
        Optional<AuthZEngine.Violation> v = eng.evaluate("ro-user", "DELETE something weird");
        assertTrue(v.isPresent());
    }
}
