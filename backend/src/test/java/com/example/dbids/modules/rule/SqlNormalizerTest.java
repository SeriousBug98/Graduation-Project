package com.example.dbids.modules.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlNormalizerTest {

    @Test
    @DisplayName("UT-03-RE/UT-04-RE 정규화: 블록/라인 주석 제거, 공백 정리, 대문자화")
    void removesCommentsAndCompactsSpaces() {
        SqlNormalizer n = new SqlNormalizer();
        String in = "/* note */  select  *  from users  -- tail\n where id=1";
        String out = n.normalize(in);
        assertEquals("SELECT * FROM USERS WHERE ID=0", out);
        assertFalse(out.contains("/*"));
        assertFalse(out.contains("--"));
        assertTrue(out.contains("SELECT"));
    }

    @Test
    @DisplayName("UT-03-RE/UT-04-RE 정규화: 문자열/숫자 리터럴 마스킹")
    void masksLiterals() {
        SqlNormalizer n = new SqlNormalizer();
        String in = "SELECT * FROM t WHERE a='abc''def' AND b=\"x\\\"y\" AND c=12345";
        String out = n.normalize(in);
        assertEquals("SELECT * FROM T WHERE A='?' AND B=\"?\" AND C=0", out);
        assertFalse(out.contains("12345"));
        assertTrue(out.contains("'?'"));
        assertTrue(out.contains("\"?\""));
    }

    @Test
    @DisplayName("UT-03-RE/UT-04-RE: 앞뒤 공백 제거 및 단일 공백화")
    void trimsAndSingleSpaces() {
        SqlNormalizer n = new SqlNormalizer();
        String in = " \n\t  SELECT   1   \n ";
        String out = n.normalize(in);
        assertEquals("SELECT 0", out);
    }
}
