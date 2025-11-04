package com.example.dbids.modules.proxy;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProxyResponseHandler {

    public static class RespMeta {
        public final int rowCount;
        public final String status; // SUCCESS | FAILURE
        public RespMeta(int rowCount, String status){ this.rowCount=rowCount; this.status=status; }
    }

    // SELECT 결과셋 기준 RowCount 계산 (간단 카운트)
    public RespMeta extract(ResultSet rs) throws SQLException {
        int cnt = 0;
        try {
            while (rs.next()) cnt++;
            return new RespMeta(cnt, "SUCCESS");
        } catch (SQLException e) {
            return new RespMeta(0, "FAILURE");
        }
    }
}
