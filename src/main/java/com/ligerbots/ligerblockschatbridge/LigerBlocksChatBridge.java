package com.ligerbots.ligerblockschatbridge;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

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
        if (channelId.isEmpty()) {
            LOGGER.error("Slack relay is enabled but relay.channelId is empty; outbound relay is disabled");
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
        SlackInboundDispatcher dispatcher = new SlackInboundDispatcher(
                channelId,
                server::execute,
                text -> server.getPlayerList().broadcastSystemMessage(Component.literal(text), false));
        try {
            inboundClient = new SlackSocketModeClient(botToken, appToken, channelId, dispatcher);
            inboundClient.start();
        } catch (Exception | LinkageError failure) {
            inboundClient = null;
            LOGGER.error("Slack inbound relay failed to start: {}", failure.getMessage());
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        OutboundMessageQueue queue = outboundQueue;
        if (queue == null) {
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
    public void onServerStopping(ServerStoppingEvent event) {
        OutboundMessageQueue queue = outboundQueue;
        outboundQueue = null;
        if (queue != null) {
            queue.close();
            LOGGER.info("Slack outbound relay stopped");
        }
        SlackSocketModeClient client = inboundClient;
        inboundClient = null;
        if (client != null) {
            client.close();
            LOGGER.info("Slack Socket Mode relay stopped");
        }
    }

}
