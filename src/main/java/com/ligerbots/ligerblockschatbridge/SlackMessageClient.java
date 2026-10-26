package com.ligerbots.ligerblockschatbridge;

import java.io.IOException;

interface SlackMessageClient {
    void sendMessage(String channelId, String text) throws IOException, InterruptedException;
}
