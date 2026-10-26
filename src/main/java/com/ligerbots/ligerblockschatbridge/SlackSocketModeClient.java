package com.ligerbots.ligerblockschatbridge;

import com.mojang.logging.LogUtils;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.model.event.MessageEvent;
import org.slf4j.Logger;

import java.util.Optional;

final class SlackSocketModeClient implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final SocketModeApp socketModeApp;

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
            Optional<InboundSlackMessage> accepted = SlackMessageFilter.accept(
                    channelId,
                    event.getChannel(),
                    event.getText(),
                    event.getUser(),
                    null,
                    event.getBotId(),
                    event.getSubtype());
            accepted.ifPresent(dispatcher::dispatch);
            return context.ack();
        });
        this.socketModeApp = new SocketModeApp(
                appToken,
                com.slack.api.socket_mode.SocketModeClient.Backend.Tyrus,
                app);
    }

    void start() {
        try {
            socketModeApp.startAsync();
            LOGGER.info("Slack Socket Mode connection attempt started");
        } catch (Exception failure) {
            LOGGER.error("Slack Socket Mode connection failed to start: {}", failure.getMessage());
            throw new IllegalStateException("Slack Socket Mode connection failed to start", failure);
        }
    }

    @Override
    public void close() {
        try {
            socketModeApp.stop();
        } catch (Exception failure) {
            LOGGER.error("Slack Socket Mode connection failed to stop: {}", failure.getMessage());
        }
    }
}
