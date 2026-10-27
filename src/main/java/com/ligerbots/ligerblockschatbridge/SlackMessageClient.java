package com.ligerbots.ligerblockschatbridge;

import java.io.IOException;

interface SlackMessageClient {
    void sendMessage(String channelId, String text) throws IOException, InterruptedException;

    default void sendEphemeralResponse(String responseUrl, String text)
            throws IOException, InterruptedException {
        throw new IOException("ephemeral Slack responses are not supported by this client");
    }
}
