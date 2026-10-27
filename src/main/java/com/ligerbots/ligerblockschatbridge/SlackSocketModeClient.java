package com.ligerbots.ligerblockschatbridge;

import com.mojang.logging.LogUtils;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.MessageEvent;
import org.slf4j.Logger;
import com.ligerbots.ligerblockschatbridge.commands.CommandRegistryManager;
import com.ligerbots.ligerblockschatbridge.commands.SlackCommandAuthorizer;
import com.ligerbots.ligerblockschatbridge.commands.SlackCommandHelpFormatter;
import com.ligerbots.ligerblockschatbridge.commands.SlackCommandParser;
import com.ligerbots.ligerblockschatbridge.commands.SlackCommandRequest;
import com.ligerbots.ligerblockschatbridge.commands.CommandResponseMode;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
        this(botToken, appToken, channelId, dispatcher, null, request -> {
        }, request -> {
        }, request -> {
        }, request -> {
        }, request -> {
        }, request -> {
        });
    }

    SlackSocketModeClient(
            String botToken,
            String appToken,
            String channelId,
            SlackInboundDispatcher dispatcher,
            CommandRegistryManager commandRegistry,
            Consumer<SlackCommandRequest> commandDispatcher,
            Consumer<SlackCommandRequest> reloadDispatcher,
            Consumer<SlackCommandRequest> configDispatcher,
            Consumer<SlackCommandRequest> adminDispatcher,
            Consumer<SlackCommandRequest> eventsDispatcher,
            Consumer<SlackCommandRequest> messagesDispatcher) throws java.io.IOException {
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
        app.command("/mc", (request, context) -> {
            SlashCommandPayload payload = request.getPayload();
            if (!channelId.equals(payload.getChannelId())) {
                LOGGER.info("Rejected Slack /mc command from channel {}", payload.getChannelId());
                return context.ack("This command is not available in this channel.");
            }
            Optional<SlackCommandParser.ParsedCommand> parsed = SlackCommandParser.parse(payload.getText());
            if (parsed.isEmpty()) {
                LOGGER.info("Rejected Slack /mc command from user {}: unknown or unconfigured command",
                        payload.getUserId());
                return context.ack("That Minecraft command is not configured.");
            }
            if ("help".equals(parsed.get().name())) {
                if (commandRegistry == null || !commandRegistry.isLoaded()) {
                    return context.ack("No Minecraft commands are configured.");
                }
                return context.ack(SlackCommandHelpFormatter.format(
                        commandRegistry.activeRegistry(), payload.getUserId()));
            }
            if ("config".equals(parsed.get().name())) {
                if (commandRegistry == null || !commandRegistry.isLoaded()) {
                    return context.ack("No Minecraft commands are configured.");
                }
                if (!commandRegistry.isAdministrator(payload.getUserId())) {
                    LOGGER.info("Rejected unauthorized Slack /mc config from user {}", payload.getUserId());
                    return context.ack("You are not authorized to view Minecraft commands.");
                }
                configDispatcher.accept(new SlackCommandRequest(
                        payload.getChannelId(),
                        payload.getUserId(),
                        payload.getUserName(),
                        payload.getResponseUrl(),
                        "config",
                        parsed.get().arguments()));
                return context.ack("Minecraft command configuration requested.");
            }
            if ("admin".equals(parsed.get().name())) {
                if (commandRegistry == null || !commandRegistry.isLoaded()) {
                    return context.ack("No Minecraft commands are configured.");
                }
                if (!commandRegistry.isAdministrator(payload.getUserId())) {
                    LOGGER.info("Rejected unauthorized Slack /mc admin command from user {}",
                            payload.getUserId());
                    return context.ack("You are not authorized to manage administrators.");
                }
                if (!isValidAdminArguments(parsed.get().arguments())) {
                    return context.ack("Usage: /mc admin add SLACK_ID [-reload] [-list]");
                }
                adminDispatcher.accept(new SlackCommandRequest(
                        payload.getChannelId(),
                        payload.getUserId(),
                        payload.getUserName(),
                        payload.getResponseUrl(),
                        "admin",
                        parsed.get().arguments()));
                return context.ack("Administrator update requested.");
            }
            if ("events".equals(parsed.get().name())) {
                if (commandRegistry == null || !commandRegistry.isLoaded()) {
                    return context.ack("No Minecraft commands are configured.");
                }
                if (!commandRegistry.isAdministrator(payload.getUserId())) {
                    LOGGER.info("Rejected unauthorized Slack /mc events from user {}", payload.getUserId());
                    return context.ack("You are not authorized to manage events.");
                }
                if (!isValidEventsArguments(parsed.get().arguments())) {
                    return context.ack("Usage: /mc events [all|user|server] [on|off]");
                }
                eventsDispatcher.accept(new SlackCommandRequest(
                        payload.getChannelId(),
                        payload.getUserId(),
                        payload.getUserName(),
                        payload.getResponseUrl(),
                        "events",
                        parsed.get().arguments()));
                return context.ack("Minecraft event setting update requested.");
            }
            if ("msgs".equals(parsed.get().name())) {
                if (commandRegistry == null || !commandRegistry.isLoaded()) {
                    return context.ack("No Minecraft commands are configured.");
                }
                if (!commandRegistry.isAdministrator(payload.getUserId())) {
                    LOGGER.info("Rejected unauthorized Slack /mc msgs from user {}", payload.getUserId());
                    return context.ack("You are not authorized to manage message relay.");
                }
                if (!isValidMessagesArguments(parsed.get().arguments())) {
                    return context.ack("Usage: /mc msgs [from|to] [on|off]");
                }
                messagesDispatcher.accept(new SlackCommandRequest(
                        payload.getChannelId(),
                        payload.getUserId(),
                        payload.getUserName(),
                        payload.getResponseUrl(),
                        "msgs",
                        parsed.get().arguments()));
                return context.ack("Minecraft message relay setting update requested.");
            }
            if (commandRegistry == null || !commandRegistry.isLoaded()) {
                LOGGER.info("Rejected Slack /mc command from user {}: unknown or unconfigured command",
                        payload.getUserId());
                return context.ack("That Minecraft command is not configured.");
            }
            if ("reload".equals(parsed.get().name())) {
                if (!commandRegistry.isAdministrator(payload.getUserId())) {
                    LOGGER.info("Rejected unauthorized Slack /mc reload from user {}", payload.getUserId());
                    return context.ack("You are not authorized to reload Minecraft commands.");
                }
                reloadDispatcher.accept(new SlackCommandRequest(
                        payload.getChannelId(),
                        payload.getUserId(),
                        payload.getUserName(),
                        payload.getResponseUrl(),
                        "reload",
                        parsed.get().arguments()));
                return context.ack("Minecraft command configuration reload requested.");
            }
            var resolution = commandRegistry.activeRegistry().resolve(parsed.get().name());
            if (resolution.isAmbiguous()) {
                LOGGER.info("Rejected ambiguous Slack /mc command prefix {} from user {}",
                        parsed.get().name(), payload.getUserId());
                return context.ack("That command prefix is ambiguous. Matches: "
                        + String.join(", ", resolution.ambiguousNames()));
            }
            if (!resolution.isMatch()
                    || !SlackCommandAuthorizer.isAuthorized(
                            commandRegistry.activeRegistry(), resolution.mapping(), payload.getUserId(), Set.of())) {
                LOGGER.info("Rejected unauthorized Slack /mc command {} from user {}",
                        parsed.get().name(), payload.getUserId());
                return context.ack("You are not authorized to use that Minecraft command.");
            }
            commandDispatcher.accept(new SlackCommandRequest(
                    payload.getChannelId(),
                    payload.getUserId(),
                    payload.getUserName(),
                    payload.getResponseUrl(),
                    resolution.mapping().name(),
                    parsed.get().arguments(),
                    resolution.mapping().responseMode()));
            LOGGER.info("Accepted Slack /mc command {} from user {}",
                    parsed.get().name(), payload.getUserId());
            return context.ack("Minecraft command accepted.");
        });
        this.socketModeApp = new SocketModeApp(
                appToken,
                com.slack.api.socket_mode.SocketModeClient.Backend.Tyrus,
                app);
    }

    private static boolean isValidAdminArguments(java.util.List<String> arguments) {
        if (arguments.isEmpty()) {
            return false;
        }
        if ("list".equalsIgnoreCase(arguments.get(0))) {
            return arguments.size() == 1;
        }
        if (!("add".equalsIgnoreCase(arguments.get(0)) || "del".equalsIgnoreCase(arguments.get(0)))
                || arguments.size() < 2
                || !arguments.get(1).matches("[UW][A-Z0-9]{8,20}")) {
            return false;
        }
        return arguments.subList(2, arguments.size()).stream()
                .allMatch(argument -> "-reload".equals(argument) || "-list".equals(argument));
    }

    private static boolean isValidEventsArguments(java.util.List<String> arguments) {
        return arguments.size() == 2
                && java.util.Set.of("all", "user", "server").contains(arguments.get(0).toLowerCase())
                && java.util.Set.of("on", "off").contains(arguments.get(1).toLowerCase());
    }

    private static boolean isValidMessagesArguments(java.util.List<String> arguments) {
        return arguments.size() == 2
                && java.util.Set.of("from", "to").contains(arguments.get(0).toLowerCase())
                && java.util.Set.of("on", "off").contains(arguments.get(1).toLowerCase());
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
