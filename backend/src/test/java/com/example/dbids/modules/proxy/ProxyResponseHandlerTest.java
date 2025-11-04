package com.example.dbids.modules.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProxyResponseHandlerTest {

    @Test
    @DisplayName("UT-02: ResultSet 10행 → RowCount=10, Status=SUCCESS")
    void rowCountTenSuccess() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true,true,true,true,true,true,true,true,true,true,false);

        ProxyResponseHandler handler = new ProxyResponseHandler();
        ProxyResponseHandler.RespMeta meta = handler.extract(rs);

        assertEquals(10, meta.rowCount);
        assertEquals("SUCCESS", meta.status);
    }

    @Test
    @DisplayName("UT-02: ResultSet 처리 중 예외 → RowCount=0, Status=FAILURE")
    void failureReturnsZeroAndFailure() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenThrow(new SQLException("boom"));

        ProxyResponseHandler handler = new ProxyResponseHandler();
        ProxyResponseHandler.RespMeta meta = handler.extract(rs);

        assertEquals(0, meta.rowCount);
        assertEquals("FAILURE", meta.status);
    }
}
