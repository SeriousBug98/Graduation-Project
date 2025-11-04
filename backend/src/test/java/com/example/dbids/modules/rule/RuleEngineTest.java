package com.example.dbids.modules.rule;

import com.example.dbids.sqlite.model.DetectionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    @Test
    @DisplayName("UT-03-RE: DROP TABLE → 매칭 성공, severity=HIGH")
    void detectsDropTableAsHigh() {
        SqlNormalizer n = new SqlNormalizer();
        String normalized = n.normalize("/*c*/  drop   table   students;  ");
        RuleEngine engine = new RuleEngine(RuleSetProvider.defaultRules());

        Optional<RuleEngine.Match> m = engine.evaluate(normalized);
        assertTrue(m.isPresent(), "패턴 매칭은 되어야 한다");
        assertEquals(DetectionEvent.Severity.HIGH, m.get().severity, "DROP TABLE은 HIGH여야 한다");
        assertTrue(normalized.contains("DROP TABLE"));
    }

    @Test
    @DisplayName("UT-03-RE: UNION SELECT → 매칭 성공, severity=MEDIUM")
    void detectsUnionSelectAsMedium() {
        SqlNormalizer n = new SqlNormalizer();
        String normalized = n.normalize("select a from t UNION    SELECT b from u -- inject");
        RuleEngine engine = new RuleEngine(RuleSetProvider.defaultRules());

        var m = engine.evaluate(normalized);
        assertTrue(m.isPresent());
        assertEquals(DetectionEvent.Severity.MEDIUM, m.get().severity);
        assertTrue(normalized.contains("UNION SELECT"));
    }

    @Test
    @DisplayName("UT-03-RE: OR 1=1 → 매칭 성공, severity=MEDIUM (공백/대소문자 변형 포함)")
    void detectsOr1Eq1AsMedium() {
        SqlNormalizer n = new SqlNormalizer();
        String normalized = n.normalize("SeLeCt * From T Where name='abc' OR   1 = 1 /*hack*/");
        RuleEngine engine = new RuleEngine(RuleSetProvider.defaultRules());

        var m = engine.evaluate(normalized);
        assertTrue(m.isPresent());
        assertEquals(DetectionEvent.Severity.MEDIUM, m.get().severity);
        // 정규화 결과에는 숫자 마스킹되어 0으로 변환됨
        assertTrue(normalized.contains("OR 0 = 0"));
    }

    @Test
    @DisplayName("UT-03-RE: SLEEP() → 매칭 성공, severity=LOW (시간 지연 유도 함수)")
    void detectsSleepAsLow() {
        SqlNormalizer n = new SqlNormalizer();
        String normalized = n.normalize("SELECT * FROM users WHERE id=1 OR SLEEP(5)");
        RuleEngine engine = new RuleEngine(RuleSetProvider.defaultRules());

        var m = engine.evaluate(normalized);
        assertTrue(m.isPresent());
        assertEquals(DetectionEvent.Severity.LOW, m.get().severity);
        assertTrue(normalized.contains("SLEEP("));
    }

    @Test
    @DisplayName("UT-03-RE: 복수 패턴 동시 매칭 시 가장 높은 severity를 선택한다")
    void choosesHighestSeverityOnMultiMatch() {
        SqlNormalizer n = new SqlNormalizer();
        // DROP TABLE(HIGH) + UNION SELECT(MEDIUM) 동시 포함
        String normalized = n.normalize("DROP TABLE x; SELECT * FROM a UNION SELECT * FROM b;");
        RuleEngine engine = new RuleEngine(RuleSetProvider.defaultRules());

        var m = engine.evaluate(normalized);
        assertTrue(m.isPresent());
        assertEquals(DetectionEvent.Severity.HIGH, m.get().severity);
        assertTrue(normalized.contains("DROP TABLE"));
        assertTrue(normalized.contains("UNION SELECT"));
    }

    @Test
    @DisplayName("UT-04-RE: 정상 SELECT → 매칭 없음")
    void normalSelectHasNoMatch() {
        SqlNormalizer n = new SqlNormalizer();
        String normalized = n.normalize("SELECT name FROM users;");
        RuleEngine engine = new RuleEngine(RuleSetProvider.defaultRules());

        Optional<RuleEngine.Match> m = engine.evaluate(normalized);
        assertTrue(m.isEmpty(), "정상 쿼리는 매칭되지 않아야 한다");
    }
}
