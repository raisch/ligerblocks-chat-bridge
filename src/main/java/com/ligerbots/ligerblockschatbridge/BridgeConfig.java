package com.ligerbots.ligerblockschatbridge;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BridgeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> CHANNEL_ID;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_FORMAT;
    public static final ModConfigSpec.IntValue QUEUE_CAPACITY;
    public static final ModConfigSpec.BooleanValue JOIN_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue LEAVE_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue DEATH_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue ADVANCEMENT_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue SERVER_START_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue SERVER_STOP_NOTIFICATIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("relay");
        ENABLED = builder
                .comment("Enable outbound Minecraft chat relay to Slack.")
                .define("enabled", false);
        CHANNEL_ID = builder
                .comment("Slack channel ID receiving Minecraft chat messages.")
                .define("channelId", "");
        MESSAGE_FORMAT = builder
                .comment("Outbound format. Supported placeholders: {player}, {message}.")
                .define("messageFormat", "<{player}> {message}");
        QUEUE_CAPACITY = builder
                .comment("Maximum number of outbound messages waiting for delivery.")
                .defineInRange("queueCapacity", 256, 1, 10_000);
        JOIN_NOTIFICATIONS = builder
                .comment("Send player join notifications to Slack.")
                .define("joinNotifications", true);
        LEAVE_NOTIFICATIONS = builder
                .comment("Send player leave notifications to Slack.")
                .define("leaveNotifications", true);
        DEATH_NOTIFICATIONS = builder
                .comment("Send player death notifications to Slack.")
                .define("deathNotifications", true);
        ADVANCEMENT_NOTIFICATIONS = builder
                .comment("Send advancement notifications to Slack.")
                .define("advancementNotifications", true);
        SERVER_START_NOTIFICATIONS = builder
                .comment("Send a server-start notification to Slack.")
                .define("serverStartNotifications", true);
        SERVER_STOP_NOTIFICATIONS = builder
                .comment("Send a server-stop notification to Slack.")
                .define("serverStopNotifications", true);
        builder.pop();
        SPEC = builder.build();
    }

    private BridgeConfig() {
    }

    static String validateRelay(String channelId, String messageFormat) {
        return RelayConfigValidator.validate(channelId, messageFormat);
    }
}
