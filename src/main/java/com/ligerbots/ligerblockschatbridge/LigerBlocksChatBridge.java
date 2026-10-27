package com.ligerbots.ligerblockschatbridge;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

import com.ligerbots.ligerblockschatbridge.commands.CommandConfigurationException;
import com.ligerbots.ligerblockschatbridge.commands.CommandRegistry;
import com.ligerbots.ligerblockschatbridge.commands.CommandRegistryLoader;
import com.ligerbots.ligerblockschatbridge.commands.CommandRegistryManager;
import com.ligerbots.ligerblockschatbridge.commands.MinecraftCommandExecutor;

@Mod(LigerBlocksChatBridge.MOD_ID)
public class LigerBlocksChatBridge {
    public static final String MOD_ID = "ligerblocks_chat_bridge";
    private static final Logger LOGGER = LogUtils.getLogger();
    private OutboundMessageQueue outboundQueue;
    private SlackSocketModeClient inboundClient;

    public LigerBlocksChatBridge(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, BridgeConfig.SPEC);
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("LigerBlocks Chat Bridge loaded");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (!BridgeConfig.ENABLED.get()) {
            LOGGER.info("Slack relay is disabled; no Slack connection or outbound worker was started");
            return;
        }

        String channelId = BridgeConfig.CHANNEL_ID.get().trim();
        String configError = BridgeConfig.validateRelay(channelId, BridgeConfig.MESSAGE_FORMAT.get());
        if (configError != null) {
            LOGGER.error("Slack relay configuration is invalid: {}; outbound relay is disabled", configError);
            return;
        }

        String botToken;
        try {
            botToken = SecretProvider.readBotToken();
        } catch (Exception failure) {
            LOGGER.error("Slack relay is enabled but its bot token could not be loaded: {}",
                    failure.getMessage());
            return;
        }
        if (!SecretProvider.isValidBotToken(botToken)) {
            LOGGER.error("Slack relay is enabled but the configured bot token is invalid; expected a Slack bot token");
            return;
        }

        outboundQueue = new OutboundMessageQueue(
                BridgeConfig.QUEUE_CAPACITY.get(),
                new HttpSlackMessageClient(botToken));
        outboundQueue.start();
        LOGGER.info("Slack outbound relay started for configured channel");
        if (BridgeConfig.SYSTEM_EVENTS.get() && BridgeConfig.SERVER_START_NOTIFICATIONS.get()) {
            enqueueEvent(channelId, "server started");
        }

        String appToken;
        try {
            appToken = SecretProvider.readAppToken();
        } catch (Exception failure) {
            LOGGER.error("Slack inbound relay is disabled because its app token could not be loaded: {}",
                    failure.getMessage());
            return;
        }
        if (!SecretProvider.isValidAppToken(appToken)) {
            LOGGER.error("Slack inbound relay is disabled because the configured app token is invalid; expected an app-level token");
            return;
        }

        MinecraftServer server = event.getServer();
        CommandRegistryManager commandRegistry = loadCommandRegistry();
        MinecraftCommandExecutor commandExecutor = commandRegistry == null
                ? null
                : new MinecraftCommandExecutor(server, commandRegistry::activeRegistry, this::enqueueCommandResult);
        SlackInboundDispatcher dispatcher = new SlackInboundDispatcher(
                channelId,
                server::execute,
                text -> server.getPlayerList().broadcastSystemMessage(Component.literal(text), false),
                BridgeConfig.SLACK_TO_MINECRAFT::get);
        try {
            inboundClient = new SlackSocketModeClient(
                    botToken,
                    appToken,
                    channelId,
                    dispatcher,
                    commandRegistry,
                    request -> {
                        if (commandExecutor != null) {
                            commandExecutor.execute(request);
                        }
                    },
                    request -> reloadCommandRegistry(server, commandRegistry, request));
            inboundClient.start();
        } catch (Exception | LinkageError failure) {
            inboundClient = null;
            LOGGER.error("Slack inbound relay failed to start", failure);
        }
    }

    private CommandRegistryManager loadCommandRegistry() {
        Path configPath = Path.of("config", "ligerblocks_chat_bridge-slack-commands.json");
        CommandRegistryManager manager = new CommandRegistryManager(configPath);
        if (Files.exists(configPath)) {
            try {
                manager.load();
                return manager;
            } catch (Exception failure) {
                LOGGER.error("Slack command configuration is invalid; /mc commands are disabled: {}",
                        failure.getMessage());
                return null;
            }
        }
        try {
            LOGGER.info("Slack command configuration not found; using bundled command defaults");
            manager.load(CommandRegistryLoader.loadResource("/default-slack-commands.json"));
            return manager;
        } catch (Exception failure) {
            LOGGER.error("Bundled Slack command defaults could not be loaded: {}",
                    failure.getMessage());
            return null;
        }
    }

    private void reloadCommandRegistry(
            MinecraftServer server,
            CommandRegistryManager manager,
            com.ligerbots.ligerblockschatbridge.commands.SlackCommandRequest request) {
        server.execute(() -> {
            try {
                manager.reload();
                enqueueCommandResult(new MinecraftCommandExecutor.CommandExecutionResult(
                        request, "Minecraft command configuration reloaded."));
            } catch (Exception failure) {
                LOGGER.error("Slack command configuration reload failed: {}", failure.getMessage());
                enqueueCommandResult(new MinecraftCommandExecutor.CommandExecutionResult(
                        request, "Minecraft command configuration reload failed; previous configuration retained."));
            }
        });
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        OutboundMessageQueue queue = outboundQueue;
        if (queue == null || !BridgeConfig.MINECRAFT_TO_SLACK.get()) {
            return;
        }

        ServerPlayer player = event.getPlayer();
        String text = MessageFormatter.format(
                BridgeConfig.MESSAGE_FORMAT.get(),
                player.getGameProfile().getName(),
                event.getRawText());
        queue.enqueue(new OutboundMessage(BridgeConfig.CHANNEL_ID.get().trim(), text));
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (BridgeConfig.USER_EVENTS.get() && BridgeConfig.JOIN_NOTIFICATIONS.get()
                && event.getEntity() instanceof ServerPlayer player) {
            enqueueEvent(player.getGameProfile().getName() + " joined");
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (BridgeConfig.USER_EVENTS.get() && BridgeConfig.LEAVE_NOTIFICATIONS.get()
                && event.getEntity() instanceof ServerPlayer player) {
            enqueueEvent(player.getGameProfile().getName() + " left");
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (BridgeConfig.USER_EVENTS.get() && BridgeConfig.DEATH_NOTIFICATIONS.get()
                && event.getEntity() instanceof ServerPlayer player) {
            enqueueEvent(player.getCombatTracker().getDeathMessage().getString());
        }
    }

    @SubscribeEvent
    public void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (BridgeConfig.USER_EVENTS.get() && BridgeConfig.ADVANCEMENT_NOTIFICATIONS.get()
                && event.getEntity() instanceof ServerPlayer player) {
            String advancement = Advancement.name(event.getAdvancement()).getString();
            enqueueEvent(player.getGameProfile().getName() + " earned advancement: " + advancement);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        OutboundMessageQueue queue = outboundQueue;
        outboundQueue = null;
        if (queue != null) {
            if (BridgeConfig.MINECRAFT_TO_SLACK.get()
                    && BridgeConfig.SYSTEM_EVENTS.get()
                    && BridgeConfig.SERVER_STOP_NOTIFICATIONS.get()) {
                LOGGER.info("Sending Slack server-stop notification before relay shutdown");
                queue.closeWithFinalMessage(eventMessage("server stopping"));
            } else {
                queue.close();
            }
            LOGGER.info("Slack outbound relay stopped");
        }
        SlackSocketModeClient client = inboundClient;
        inboundClient = null;
        if (client != null) {
            client.close();
            LOGGER.info("Slack Socket Mode relay stopped");
        }
    }

    private void enqueueEvent(String event) {
        enqueueEvent(BridgeConfig.CHANNEL_ID.get().trim(), event);
    }

    private void enqueueEvent(String channelId, String event) {
        OutboundMessageQueue queue = outboundQueue;
        if (queue != null && BridgeConfig.MINECRAFT_TO_SLACK.get()) {
            queue.enqueue(eventMessage(channelId, event));
        }
    }

    private void enqueueCommandResult(MinecraftCommandExecutor.CommandExecutionResult result) {
        OutboundMessageQueue queue = outboundQueue;
        if (queue != null) {
            String requester = result.request().userName();
            if (requester == null || requester.isBlank()) {
                requester = result.request().userId();
            }
            queue.enqueue(new OutboundMessage(
                    result.request().channelId(),
                    "[Slack] <" + requester + "> " + result.text()));
        }
    }

    private OutboundMessage eventMessage(String event) {
        return eventMessage(BridgeConfig.CHANNEL_ID.get().trim(), event);
    }

    private OutboundMessage eventMessage(String channelId, String event) {
        return new OutboundMessage(channelId, MessageFormatter.formatEvent(event));
    }

}
