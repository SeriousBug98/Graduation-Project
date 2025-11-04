package com.example.dbids.modules.notify;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class SlackSender {
    private final NotifierProperties props;
    private final HttpClient client = HttpClient.newHttpClient();

    public SlackSender(NotifierProperties props) { this.props = props; }

    public void send(String text) {
        var cfg = props.getSlack();
        if (!cfg.isEnabled()) return;
        if (cfg.getWebhookUrl() == null || cfg.getWebhookUrl().isBlank())
            throw new IllegalStateException("dbids.notifier.slack.webhook-url is not set");

        String payload = "{\"text\":" + json(text) + "}";

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getWebhookUrl()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new RuntimeException("SLACK_WEBHOOK_HTTP_" + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("SLACK_WEBHOOK_ERROR: " + e.getMessage(), e);
        }
    }

    private String json(String s) {
        String esc = s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
        return "\"" + esc + "\"";
    }
}
