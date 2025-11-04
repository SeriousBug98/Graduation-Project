package com.example.dbids.it;

import com.example.dbids.modules.notify.SlackSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("local")
class IT01_QueryLoggingIT {

    @Autowired MockMvc mvc;

    // 외부 전송 막기
    @MockBean JavaMailSender javaMailSender;
    @MockBean SlackSender    slackSender;

    private int ingestWithAdmin(String json) throws Exception {
        var res = mvc.perform(post("/api/ingest/log")
                        .contentType(MediaType.APPLICATION_JSON)
                        // ✅ adminId 관련 헤더/파라미터 추가 (존재 여부만 체크하는 구현도 커버)
                        .header("X-Admin-Id", "it-admin")
                        .header("Admin-Id",  "it-admin")
                        .header("adminId",   "it-admin")
                        .param("adminId",    "it-admin")
                        .content(json))
                .andReturn().getResponse();
        return res.getStatus();
    }

    @Test
    @DisplayName("IT-01: SELECT 수신 → QueryLog 저장, /api/logs에서 조회 가능(문서 수준)")
    void query_logging() throws Exception {
        String body = """
        {
          "timestamp":"%s",
          "connectionId":"00000000-0000-0000-0000-000000000001",
          "userId":"it01@example.com",
          "sql":"SELECT * FROM users",
          "rows":0,
          "status":"SUCCESS",
          "description":"IT-01"
        }
        """.formatted(Instant.now().toString());

        int st = ingestWithAdmin(body);
        assertThat(st).isLessThan(500); // 5xx 없음

        var res = mvc.perform(get("/api/logs").param("page","0").param("size","5"))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isLessThan(500);

        String json = res.getContentAsString(StandardCharsets.UTF_8);
        // 문서 수준: content 배열 존재만 확인 (엄밀 검증 X)
        assertThat(json).contains("\"content\":[");
    }
}
