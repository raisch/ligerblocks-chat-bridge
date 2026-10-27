package com.ligerbots.ligerblockschatbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BridgeConfigTest {
    @Test
    void acceptsValidRelayConfiguration() {
        assertNull(RelayConfigValidator.validate("C0123456789", "<{player}> {message}"));
    }

    @Test
    void rejectsInvalidChannelId() {
        assertNotNull(RelayConfigValidator.validate("testing", "<{player}> {message}"));
    }

    @Test
    void rejectsIncompleteMessageFormat() {
        assertNotNull(RelayConfigValidator.validate("C0123456789", "{message}"));
    }
}
