package com.ligerbots.ligerblockschatbridge.commands;

import java.util.List;

public record SlackCommandRequest(
        String channelId,
        String userId,
        String userName,
        String responseUrl,
        String commandName,
        List<String> arguments,
        CommandResponseMode responseMode) {
    public SlackCommandRequest(
            String channelId,
            String userId,
            String userName,
            String responseUrl,
            String commandName,
            List<String> arguments) {
        this(channelId, userId, userName, responseUrl, commandName, arguments, CommandResponseMode.PRIVATE);
    }

    public SlackCommandRequest {
        arguments = List.copyOf(arguments);
        responseMode = responseMode == null ? CommandResponseMode.PRIVATE : responseMode;
    }
}
