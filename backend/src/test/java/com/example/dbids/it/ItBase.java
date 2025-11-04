// src/test/java/com/example/dbids/it/ItBase.java
package com.example.dbids.it;

import com.example.dbids.DbidsApplication;
import com.example.dbids.modules.notify.SlackSender;
import com.example.dbids.sqlite.repository.DetectionEventRepository;
import com.example.dbids.sqlite.repository.NotificationLogRepository;
import com.example.dbids.sqlite.repository.QueryLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = DbidsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
public abstract class ItBase {

    @Autowired protected MockMvc mvc;
    @Autowired(required = false) protected ObjectMapper om = new ObjectMapper();

    // 외부 전송 차단
    @MockBean protected JavaMailSender javaMailSender;
    @MockBean protected SlackSender    slackSender;

    // 선택 리포지토리
    @Autowired(required = false) protected QueryLogRepository        logRepo;
    @Autowired(required = false) protected DetectionEventRepository  eventRepo;
    @Autowired(required = false) protected NotificationLogRepository notifRepo;

    // 매 실행마다 새 SQLite 파일
    private static final Path IT_DB = Path.of(
            "build", "it-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".db"
    ).toAbsolutePath();

    // ✅ 테스트에서 사용할 고정 adminId (유효성 검증 없이 "존재 여부"만 확인하는 구현을 커버)
    protected static final String TEST_ADMIN_ID = "it-admin-for-tests";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        try { Files.deleteIfExists(IT_DB); } catch (Exception ignore) {}
        r.add("spring.datasource.sqlite.jdbc-url", () -> "jdbc:sqlite:" + IT_DB);
        r.add("spring.jpa.hibernate.ddl-auto",     () -> "create-drop");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.community.dialect.SQLiteDialect");
        r.add("spring.main.web-application-type",  () -> "servlet");
        r.add("spring.mail.username",              () -> "");
    }

    @BeforeEach void setUp() { }

    protected void expectNot5xx(int status) {
        assertThat(status)
                .withFailMessage("Unexpected 5xx status: %s", status)
                .isLessThan(500);
    }

    protected void postJson(String path, Object body) throws Exception {
        var mapper = (om != null ? om : new com.fasterxml.jackson.databind.ObjectMapper());
        var res = mvc.perform(
                post(path)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        // ✅ 여기만 추가
                        .header("X-Admin-Id", "it-admin")
                        .header("Admin-Id",  "it-admin")
                        .header("adminId",   "it-admin")
                        .param("adminId",    "it-admin")
                        .content(mapper.writeValueAsBytes(body))
        ).andReturn().getResponse();
        expectNot5xx(res.getStatus());
    }

}
