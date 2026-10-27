package com.ligerbots.ligerblockschatbridge;

final class RelayConfigValidator {
    private static final String CHANNEL_ID_PATTERN = "^[CG][A-Z0-9]{8,}$";

    private RelayConfigValidator() {
    }

    static String validate(String channelId, String messageFormat) {
        if (channelId == null || !channelId.trim().matches(CHANNEL_ID_PATTERN)) {
            return "relay.channelId must be a Slack channel ID such as C0123456789";
        }
        if (messageFormat == null || messageFormat.isBlank()) {
            return "relay.messageFormat must not be blank";
        }
        if (!messageFormat.contains("{player}") || !messageFormat.contains("{message}")) {
            return "relay.messageFormat must contain both {player} and {message}";
        }
        return null;
    }
}
