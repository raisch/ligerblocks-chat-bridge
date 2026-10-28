package com.ligerbots.ligerblockschatbridge.commands;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the command forms in the supplied command configuration.
 * These cases can also be run manually through Slack after /mc reload.
 */
class CommandPatternJigTest {
    private static CommandRegistry registry;

    @BeforeAll
    static void loadJigConfiguration() throws Exception {
        registry = CommandRegistryLoader.load(Path.of(
                "config/ligerblocks_chat_bridge-slack-commands.json"));
    }

    @Test
    void validatesCommandWithNoArguments() throws Exception {
        assertExpansion("players", List.of(), "list");
    }

    @Test
    void validatesRestArgument() throws Exception {
        assertExpansion("test-rest", List.of("hello", "from", "Slack"), "say hello from Slack");
    }

    @Test
    void validatesRequiredOneOrMoreArguments() throws Exception {
        assertExpansion("test-required-many", List.of("hello", "brave", "world"),
                "say required=hello brave world");
        assertThrows(CommandConfigurationException.class,
                () -> expand("test-required-many", List.of()));
    }

    @Test
    void validatesOptionalOneOrMoreArguments() throws Exception {
        assertExpansion("test-optional-many", List.of(), "say optional=");
        assertExpansion("test-optional-many", List.of("one", "two"), "say optional=one two");
    }

    @Test
    void validatesConstrainedOption() throws Exception {
        assertExpansion("test-option", List.of("rain"), "weather rain");
        assertThrows(CommandConfigurationException.class,
                () -> expand("test-option", List.of("snow")));
    }

    @Test
    void validatesRequiredOneOrMoreFollowedByOption() throws Exception {
        assertExpansion("test-many-option", List.of("hello", "brave", "bold"),
                "say words=hello brave style=bold");
        assertThrows(CommandConfigurationException.class,
                () -> expand("test-many-option", List.of("hello", "brave", "ornate")));
    }

    @Test
    void validatesSlackTokenizationForQuotedRestArguments() throws Exception {
        SlackCommandParser.ParsedCommand request = SlackCommandParser.parse(
                "test-rest \"hello from a quoted message\"").orElseThrow();
        assertExpansion(request.name(), request.arguments(), "say hello from a quoted message");
    }

    private static void assertExpansion(String name, List<String> arguments, String expected)
            throws CommandConfigurationException {
        assertEquals(List.of(expected), expand(name, arguments));
    }

    private static List<String> expand(String name, List<String> arguments)
            throws CommandConfigurationException {
        return CommandArgumentExpander.expand(registry.find(name).orElseThrow(), arguments);
    }
}
