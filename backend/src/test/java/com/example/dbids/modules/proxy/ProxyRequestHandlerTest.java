package com.example.dbids.modules.proxy;

import com.example.dbids.sqlite.model.QueryLog;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ProxyRequestHandlerTest {

    @Test
    @DisplayName("UT-01: 원문은 UTF-8로 추출되고 STATUS=SUCCESS")
    void buildQueryLogSetsSuccess() {
        ProxyRequestHandler h = new ProxyRequestHandler();
        byte[] bytes = "SELECT * FROM users;".getBytes(StandardCharsets.UTF_8);
        String txId = UUID.randomUUID().toString();

        QueryLog ql = h.buildQueryLog(bytes, "alice", txId, true);

        assertEquals("SELECT * FROM users;", ql.getSqlRaw());
        assertEquals(QueryLog.Status.SUCCESS, ql.getStatus());
        assertEquals("alice", ql.getUserId());
        assertEquals(txId, ql.getId());
    }

    @Test
    @DisplayName("UT-01: 비정상 UTF-8 바이트 시퀀스는 거부한다")
    void rejectsInvalidUtf8() {
        // 잘못된 UTF-8 시퀀스 (0xC3 다음 바이트가 잘못됨)
        byte[] invalid = new byte[]{ (byte)0xC3, 0x28 };
        ProxyRequestHandler h = new ProxyRequestHandler();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> h.extractSqlRaw(invalid));
        assertTrue(ex.getMessage().contains("invalid UTF-8"));
    }
}
