package com.ligerbots.ligerblockschatbridge.commands;

import java.util.List;

public record SlackCommandRequest(
        String channelId,
        String userId,
        String userName,
        String responseUrl,
        String commandName,
        List<String> arguments) {
    public SlackCommandRequest {
        arguments = List.copyOf(arguments);
    }
}
