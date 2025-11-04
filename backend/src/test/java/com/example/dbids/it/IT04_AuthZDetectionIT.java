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
class IT04_AuthZDetectionIT {

    @Autowired MockMvc mvc;

    @MockBean JavaMailSender javaMailSender;
    @MockBean SlackSender    slackSender;

    private int ingestWithAdmin(String json) throws Exception {
        var res = mvc.perform(post("/api/ingest/log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Admin-Id", "it-admin")
                        .header("Admin-Id",  "it-admin")
                        .header("adminId",   "it-admin")
                        .param("adminId",    "it-admin")
                        .content(json))
                .andReturn().getResponse();
        return res.getStatus();
    }

    @Test
    @DisplayName("IT-04: READ_ONLY 계정의 INSERT → AUTHZ 탐지 경로(5xx 없음만 확인)")
    void authz_detection_smoke() throws Exception {
        String body = """
        {
          "timestamp":"%s",
          "connectionId":"00000000-0000-0000-0000-000000000004",
          "userId":"readonly@example.com",
          "sql":"INSERT INTO users(id) VALUES(1)",
          "rows":0,
          "status":"SUCCESS",
          "description":"IT-04"
        }
        """.formatted(Instant.now().toString());

        int st = ingestWithAdmin(body);
        assertThat(st).isLessThan(500);

        var res = mvc.perform(get("/api/events").param("page","0").param("size","5"))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isLessThan(500);

        String json = res.getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"content\":[");
    }
}
