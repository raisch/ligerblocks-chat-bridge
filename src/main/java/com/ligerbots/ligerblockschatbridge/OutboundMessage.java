package com.ligerbots.ligerblockschatbridge;

record OutboundMessage(String channelId, String responseUrl, String text) {
    OutboundMessage(String channelId, String text) {
        this(channelId, null, text);
    }

    static OutboundMessage channel(String channelId, String text) {
        return new OutboundMessage(channelId, null, text);
    }

    static OutboundMessage ephemeral(String responseUrl, String text) {
        return new OutboundMessage(null, responseUrl, text);
    }

    boolean isEphemeral() {
        return responseUrl != null && !responseUrl.isBlank();
    }
}
