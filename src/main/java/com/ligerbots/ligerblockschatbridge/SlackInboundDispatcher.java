package com.ligerbots.ligerblockschatbridge;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

final class SlackInboundDispatcher {
    private final String channelId;
    private final Consumer<Runnable> serverExecutor;
    private final Consumer<String> broadcaster;
    private final BooleanSupplier enabled;

    SlackInboundDispatcher(
            String channelId,
            Consumer<Runnable> serverExecutor,
            Consumer<String> broadcaster,
            BooleanSupplier enabled) {
        this.channelId = channelId;
        this.serverExecutor = serverExecutor;
        this.broadcaster = broadcaster;
        this.enabled = enabled;
    }

    void dispatch(InboundSlackMessage message) {
        if (!enabled.getAsBoolean() || !message.channelId().equals(channelId)) {
            return;
        }
        String formatted = "[Slack] <" + message.identity() + "> " + message.text();
        serverExecutor.accept(() -> broadcaster.accept(formatted));
    }
}
