package com.ligerbots.ligerblockschatbridge;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BridgeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> CHANNEL_ID;
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_FORMAT;
    public static final ModConfigSpec.IntValue QUEUE_CAPACITY;

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
        builder.pop();
        SPEC = builder.build();
    }

    private BridgeConfig() {
    }
}
