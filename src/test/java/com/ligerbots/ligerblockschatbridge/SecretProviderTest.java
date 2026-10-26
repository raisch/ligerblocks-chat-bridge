package com.ligerbots.ligerblockschatbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretProviderTest {
    @Test
    void validatesSlackBotTokenShapeWithoutLoggingOrExposingIt() {
        assertTrue(SecretProvider.isValidBotToken("xoxb-test-token"));
        assertFalse(SecretProvider.isValidBotToken("xapp-test-token"));
        assertFalse(SecretProvider.isValidBotToken("xoxb-"));
    }
}
