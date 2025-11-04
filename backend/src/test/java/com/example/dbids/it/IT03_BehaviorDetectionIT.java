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
class IT03_BehaviorDetectionIT {

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
    @DisplayName("IT-03: 동일 사용자 버스트(6회) → BEHAVIOR 이벤트(문서 수준)")
    void behavior_detection() throws Exception {
        for (int i = 0; i < 6; i++) {
            String body = """
            {
              "timestamp":"%s",
              "connectionId":"00000000-0000-0000-0000-000000000003",
              "userId":"it03@example.com",
              "sql":"SELECT * FROM t WHERE i=%d",
              "rows":1,
              "status":"SUCCESS",
              "description":"IT-03-%d"
            }
            """.formatted(Instant.now().toString(), i, i);

            int st = ingestWithAdmin(body);
            assertThat(st).isLessThan(500);
        }

        var res = mvc.perform(get("/api/events").param("page","0").param("size","5"))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isLessThan(500);

        String json = res.getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"content\":[");
    }
}
