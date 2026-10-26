package com.ligerbots.ligerblockschatbridge;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

final class HttpSlackMessageClient implements SlackMessageClient {
    private static final URI CHAT_POST_MESSAGE = URI.create("https://slack.com/api/chat.postMessage");
    private static final Pattern OK_RESPONSE = Pattern.compile("\"ok\"\\s*:\\s*true");
    private final HttpClient httpClient;
    private final String botToken;

    HttpSlackMessageClient(String botToken) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.botToken = botToken;
    }

    @Override
    public void sendMessage(String channelId, String text) throws IOException, InterruptedException {
        String body = "{\"channel\":\"" + escapeJson(channelId) + "\",\"text\":\""
                + escapeJson(text) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(CHAT_POST_MESSAGE)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + botToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            throw new SlackApiException("Slack rate limit response (HTTP 429)",
                    parseRetryAfterMillis(response.headers().firstValue("Retry-After").orElse(null)));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SlackApiException("Slack HTTP failure (status " + response.statusCode() + ")", 0);
        }
        if (!OK_RESPONSE.matcher(response.body()).find()) {
            throw new SlackApiException("Slack API rejected chat.postMessage response", 0);
        }
    }

    private static long parseRetryAfterMillis(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(value.trim()) * 1_000);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
