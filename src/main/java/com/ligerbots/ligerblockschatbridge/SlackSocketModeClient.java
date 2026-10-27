package com.ligerbots.ligerblockschatbridge;

import com.mojang.logging.LogUtils;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.MessageEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class SlackSocketModeClient implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, String> userNameCache = new ConcurrentHashMap<>();
    private final SocketModeApp socketModeApp;
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean hasConnected = new AtomicBoolean();
    private volatile boolean closing;

    SlackSocketModeClient(
            String botToken,
            String appToken,
            String channelId,
            SlackInboundDispatcher dispatcher) throws java.io.IOException {
        App app = new App(AppConfig.builder()
                .singleTeamBotToken(botToken)
                .build());
        app.event(MessageEvent.class, (request, context) -> {
            MessageEvent event = request.getEvent();
            MethodsClient slackClient = context.client();
            String identity = resolveIdentity(slackClient, event.getUser());
            Optional<InboundSlackMessage> accepted = SlackMessageFilter.accept(
                    channelId,
                    event.getChannel(),
                    event.getText(),
                    event.getUser(),
                    identity,
                    event.getBotId(),
                    event.getSubtype());
            LOGGER.info("Received Slack message from {} in channel {} (accepted={}): {}",
                    identity, event.getChannel(), accepted.isPresent(), event.getText());
            accepted.ifPresent(dispatcher::dispatch);
            return context.ack();
        });
        this.socketModeApp = new SocketModeApp(
                appToken,
                com.slack.api.socket_mode.SocketModeClient.Backend.Tyrus,
                app);
    }

    private String resolveIdentity(MethodsClient slackClient, String userId) {
        if (userId == null || userId.isBlank()) {
            return userId;
        }

        String cached = userNameCache.get(userId);
        if (cached != null) {
            return cached;
        }

        try {
            var response = slackClient.usersInfo(request -> request.user(userId));
            if (response.isOk() && response.getUser() != null) {
                var user = response.getUser();
                String profileName = user.getProfile() == null ? null : user.getProfile().getDisplayName();
                String name = firstNonBlank(profileName, user.getRealName(), user.getName(), userId);
                userNameCache.put(userId, name);
                return name;
            }
            LOGGER.warn("Slack user lookup failed for {}: {}", userId, response.getError());
        } catch (IOException | SlackApiException failure) {
            LOGGER.warn("Slack user lookup failed for {}: {}", userId, failure.getMessage());
        }
        return userId;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    void start() {
        try {
            socketModeApp.startAsync();
            registerLifecycleListeners();
            LOGGER.info("Slack Socket Mode connection attempt started");
        } catch (Exception failure) {
            LOGGER.error("Slack Socket Mode connection failed to start", failure);
            throw new IllegalStateException("Slack Socket Mode connection failed to start", failure);
        }
    }

    private void registerLifecycleListeners() throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            var socketModeClient = socketModeApp.getClient();
            if (socketModeClient != null) {
                socketModeClient.addWebSocketErrorListener(failure ->
                        LOGGER.error("Slack Socket Mode connection error", failure));
                socketModeClient.addWebSocketCloseListener((statusCode, reason) -> {
                    connected.set(false);
                    if (closing) {
                        LOGGER.info("Slack Socket Mode connection closed during shutdown");
                    } else {
                        LOGGER.warn("Slack Socket Mode disconnected: statusCode={}, reason={}, autoReconnect={}",
                                statusCode, reason, socketModeClient.isAutoReconnectEnabled());
                    }
                });
                socketModeClient.addWebSocketMessageListener(message -> {
                    if (message.replace(" ", "").contains("\"type\":\"hello\"")) {
                        boolean wasConnected = connected.getAndSet(true);
                        boolean hadPreviousConnection = hasConnected.getAndSet(true);
                        if (!wasConnected && !hadPreviousConnection) {
                            LOGGER.info("Slack Socket Mode connected");
                        } else if (!wasConnected) {
                            LOGGER.info("Slack Socket Mode reconnected");
                        }
                    }
                });
                return;
            }
            Thread.sleep(10);
        }
        LOGGER.warn("Slack Socket Mode client was not available for lifecycle listener registration");
    }

    @Override
    public void close() {
        closing = true;
        try {
            socketModeApp.stop();
        } catch (Exception failure) {
            LOGGER.error("Slack Socket Mode connection failed to stop: {}", failure.getMessage());
        }
    }
}
