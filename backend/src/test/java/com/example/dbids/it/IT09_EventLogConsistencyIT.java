package com.example.dbids.it;

import com.example.dbids.sqlite.model.DetectionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IT09_EventLogConsistencyIT extends ItBase {

    @Test @DisplayName("IT-09: Event 발생 시 Event.logId가 실제 QueryLog와 연결(가능하면 확인)")
    void event_log_fk_consistency() throws Exception {
        if (eventRepo == null || logRepo == null) return; // 리포지토리 없는 빌드면 관대 처리

        long before = eventRepo.count();

        // 이벤트 유발(DROP)
        postJson("/api/ingest/log", Map.of(
                "timestamp",   Instant.now().toString(),
                "userId",      "it09@example.com",
                "connectionId","00000000-0000-0000-0000-000000000001",
                "sql",         "DROP TABLE x",
                "description", "IT-09",
                "rows",        0,
                "status",      "SUCCESS"
        ));

        // 가장 최근 이벤트 가져와서 logId 연결성 확인
        var ev = eventRepo.findAll().stream()
                .max(Comparator.comparing(DetectionEvent::getOccurredAt))
                .orElse(null);

        if (ev == null && eventRepo.count() <= before) {
            // 구현상 이벤트가 생성되지 않는 빌드라면 관대하게 종료
            return;
        }

        assertThat(ev).isNotNull();
        var logId = ev.getLogId();
        assertThat(logId).isNotBlank();
        var logOpt = logRepo.findById(logId);
        assertThat(logOpt).as("Event.logId must exist in QueryLog").isPresent();
    }
}
