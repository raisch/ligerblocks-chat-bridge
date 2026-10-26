package com.ligerbots.ligerblockschatbridge;

import java.io.IOException;

final class SlackApiException extends IOException {
    private final long retryAfterMillis;

    SlackApiException(String message, long retryAfterMillis) {
        super(message);
        this.retryAfterMillis = retryAfterMillis;
    }

    long retryAfterMillis() {
        return retryAfterMillis;
    }
}
