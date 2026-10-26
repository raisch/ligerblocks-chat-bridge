package com.ligerbots.ligerblockschatbridge;

import java.util.function.Consumer;

final class SlackInboundDispatcher {
    private final String channelId;
    private final Consumer<Runnable> serverExecutor;
    private final Consumer<String> broadcaster;

    SlackInboundDispatcher(
            String channelId,
            Consumer<Runnable> serverExecutor,
            Consumer<String> broadcaster) {
        this.channelId = channelId;
        this.serverExecutor = serverExecutor;
        this.broadcaster = broadcaster;
    }

    void dispatch(InboundSlackMessage message) {
        if (!message.channelId().equals(channelId)) {
            return;
        }
        String formatted = "[Slack] <" + message.identity() + "> " + message.text();
        serverExecutor.accept(() -> broadcaster.accept(formatted));
    }
}
