package com.ligerbots.ligerblockschatbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackMessageFilterTest {
    @Test
    void acceptsOnlyNormalMessagesFromConfiguredChannel() {
        assertTrue(SlackMessageFilter.accept(
                "C123", "C123", "hello", "U123", null, null, null).isPresent());
        assertFalse(SlackMessageFilter.accept(
                "C123", "C999", "hello", "U123", null, null, null).isPresent());
        assertFalse(SlackMessageFilter.accept(
                "C123", "C123", "hello", "U123", null, "B123", null).isPresent());
        assertFalse(SlackMessageFilter.accept(
                "C123", "C123", "hello", "U123", null, null, "message_changed").isPresent());
    }
}
