package com.example.dbids.modules.proxy;

import com.example.dbids.sqlite.model.QueryLog;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class ProxyRequestHandler {

    // FR-1: SQLRaw 추출 (여기선 단순 바이트→String, UTF-8 보장)
    public String extractSqlRaw(byte[] sqlBytes) {
        Utf8Validator.ensureUtf8(sqlBytes);           // SRS: 비정상 인코딩 거부
        return new String(sqlBytes, StandardCharsets.UTF_8).trim();
    }

    public QueryLog buildQueryLog(byte[] bytes, String userId, String txId, boolean forwardOk) {
        String raw = extractSqlRaw(bytes);
        QueryLog ql = new QueryLog(/* id= */ txId, /* ts= */ Instant.now().toString(),
                userId, txId, raw, /* summary= */ null, /* rowCount= */ 0,
                forwardOk ? QueryLog.Status.SUCCESS : QueryLog.Status.FAILURE);
        return ql;
    }
}
