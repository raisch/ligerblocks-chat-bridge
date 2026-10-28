package com.ligerbots.ligerblockschatbridge.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackCommandParserTest {
    @Test
    void parsesSubcommandAndArguments() {
        var parsed = SlackCommandParser.parse("  players   extra  ").orElseThrow();

        assertEquals("players", parsed.name());
        assertEquals(List.of("extra"), parsed.arguments());
    }

    @Test
    void rejectsBlankText() {
        assertTrue(SlackCommandParser.parse(" \t").isEmpty());
    }

    @Test
    void supportsQuotedArgumentsWithoutShellExpansion() {
        var parsed = SlackCommandParser.parse("say \"hello minecraft\"").orElseThrow();

        assertEquals("say", parsed.name());
        assertEquals(List.of("hello minecraft"), parsed.arguments());
    }

    @Test
    void rejectsUnterminatedQuotes() {
        assertTrue(SlackCommandParser.parse("say \"unfinished").isEmpty());
    }
}
