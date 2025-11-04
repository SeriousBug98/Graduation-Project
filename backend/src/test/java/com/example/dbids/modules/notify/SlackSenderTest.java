// src/test/java/com/example/dbids/modules/notify/SlackSenderTest.java
package com.example.dbids.modules.notify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SlackSenderTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>("");
    private final AtomicInteger hitCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;

        server.createContext("/ok", new RecordingHandler(200, "ok"));
        server.createContext("/err", new RecordingHandler(500, "err"));
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        lastBody.set("");
        hitCount.set(0);
    }

    private static NotifierProperties props(boolean enabled, String webhookUrl) {
        NotifierProperties p = new NotifierProperties();
        NotifierProperties.Slack s = new NotifierProperties.Slack();
        s.setEnabled(enabled);
        s.setWebhookUrl(webhookUrl);
        p.setSlack(s);
        return p;
    }

    @Test
    @DisplayName("UT-09-A: SlackSender 200 OK → 전송 성공(SENT), payload에 text 포함")
    void send_ok() {
        SlackSender sender = new SlackSender(props(true, baseUrl + "/ok"));
        sender.send("hello world");

        assertTrue(hitCount.get() >= 1, "웹훅이 호출되어야 한다");
        String body = lastBody.get();
        assertNotNull(body);
        assertTrue(body.contains("\"text\""), "payload는 text 필드를 포함해야 함");
        assertTrue(body.contains("hello world".replace("\"", "\\\"")));
    }

    @Test
    @DisplayName("UT-09-B: SlackSender 500 응답 → RuntimeException(SLACK_WEBHOOK_HTTP_500)")
    void send_httpError() {
        SlackSender sender = new SlackSender(props(true, baseUrl + "/err"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> sender.send("oops"));
        assertTrue(ex.getMessage().contains("SLACK_WEBHOOK_HTTP_500"));
        assertTrue(hitCount.get() >= 1, "실패 케이스도 실제로 호출되어야 함");
    }

    @Test
    @DisplayName("UT-09-C: SlackSender disabled=true → 호출 없이 조용히 리턴")
    void send_disabled() {
        SlackSender sender = new SlackSender(props(false, baseUrl + "/ok"));
        sender.send("no-call");
        assertEquals(0, hitCount.get(), "disabled면 웹훅 호출이 없어야 한다");
    }

    @Test
    @DisplayName("UT-09-D: SlackSender webhook-url 누락 → IllegalStateException")
    void send_missingWebhook() {
        SlackSender sender = new SlackSender(props(true, null));
        assertThrows(IllegalStateException.class, () -> sender.send("any"));
    }

    /** 요청을 기록하고 지정된 상태코드로 응답하는 핸들러 */
    private class RecordingHandler implements HttpHandler {
        private final int status;
        private final String resp;

        private RecordingHandler(int status, String resp) {
            this.status = status;
            this.resp = resp;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            hitCount.incrementAndGet();
            byte[] req = exchange.getRequestBody().readAllBytes();
            lastBody.set(new String(req, StandardCharsets.UTF_8));

            byte[] out = resp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        }
    }
}
