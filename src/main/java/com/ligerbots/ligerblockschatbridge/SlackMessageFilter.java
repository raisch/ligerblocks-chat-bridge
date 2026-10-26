package com.ligerbots.ligerblockschatbridge;

import java.util.Optional;

final class SlackMessageFilter {
    private SlackMessageFilter() {
    }

    static Optional<InboundSlackMessage> accept(
            String configuredChannelId,
            String channelId,
            String text,
            String userId,
            String username,
            String botId,
            String subtype) {
        if (channelId == null || !channelId.equals(configuredChannelId)
                || text == null || text.isBlank()
                || botId != null && !botId.isBlank()
                || subtype != null && !subtype.isBlank()) {
            return Optional.empty();
        }

        String identity = username == null || username.isBlank() ? userId : username;
        if (identity == null || identity.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new InboundSlackMessage(channelId, text, identity));
    }
}
