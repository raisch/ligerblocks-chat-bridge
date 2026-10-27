package com.ligerbots.ligerblockschatbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatterTest {
    @Test
    void replacesSupportedPlaceholders() {
        assertEquals("[Alex] hello", MessageFormatter.format(
                "[{player}] {message}", "Alex", "hello"));
    }

    @Test
    void formatsServerEvents() {
        assertEquals("[Server] Alex joined", MessageFormatter.formatEvent("Alex joined"));
    }
}
