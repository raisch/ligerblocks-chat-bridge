package com.ligerbots.ligerblockschatbridge.commands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandArgumentExpanderTest {
    @Test
    void expandsTypedArgumentsAndMacros() throws Exception {
        CommandMapping mapping = new CommandMapping(
                "welcome",
                List.of("say Welcome {player}", "give {player} apple 1"),
                CommandPermission.ADMIN,
                Set.of(),
                Set.of(),
                Map.of("player", new CommandArgumentDefinition("player", "minecraft_username", true)),
                Set.of());

        assertEquals(
                List.of("say Welcome Steve", "give Steve apple 1"),
                CommandArgumentExpander.expand(mapping, List.of("Steve")));
    }

    @Test
    void rejectsMissingExtraAndMalformedArguments() {
        CommandMapping mapping = new CommandMapping(
                "addplayer",
                List.of("whitelist add {player}"),
                CommandPermission.ADMIN,
                Set.of(),
                Set.of(),
                Map.of("player", new CommandArgumentDefinition("player", "minecraft_username", true)),
                Set.of());

        assertThrows(CommandConfigurationException.class,
                () -> CommandArgumentExpander.expand(mapping, List.of()));
        assertThrows(CommandConfigurationException.class,
                () -> CommandArgumentExpander.expand(mapping, List.of("Steve", "extra")));
        assertThrows(CommandConfigurationException.class,
                () -> CommandArgumentExpander.expand(mapping, List.of("not valid")));
    }

    @Test
    void supportsRestOptionsAndVariadicArguments() throws Exception {
        CommandMapping rest = new CommandMapping(
                "say",
                List.of("say {message}"),
                CommandPermission.EVERYONE,
                Set.of(),
                Set.of(),
                Map.of("message", new CommandArgumentDefinition("message", "rest", true)),
                Set.of());
        CommandMapping option = new CommandMapping(
                "weather",
                List.of("weather {kind}"),
                CommandPermission.ADMIN,
                Set.of(),
                Set.of(),
                Map.of("kind", new CommandArgumentDefinition("kind", "option", true, List.of("clear", "rain"))),
                Set.of());
        CommandMapping many = new CommandMapping(
                "team",
                List.of("team add {players}"),
                CommandPermission.ADMIN,
                Set.of(),
                Set.of(),
                Map.of("players", new CommandArgumentDefinition("players", "one_or_more", true)),
                Set.of());

        assertEquals(List.of("say hello from Slack"),
                CommandArgumentExpander.expand(rest, List.of("hello", "from", "Slack")));
        assertEquals(List.of("weather clear"),
                CommandArgumentExpander.expand(option, List.of("clear")));
        assertEquals(List.of("team add Alex Steve"),
                CommandArgumentExpander.expand(many, List.of("Alex", "Steve")));
        assertThrows(CommandConfigurationException.class,
                () -> CommandArgumentExpander.expand(option, List.of("storm")));
    }

    @Test
    void supportsRequiredVariadicArgumentsFollowedByAnOption() throws Exception {
        CommandMapping mapping = new CommandMapping(
                "announce",
                List.of("say {words} ({style})"),
                CommandPermission.ADMIN,
                Set.of(),
                Set.of(),
                Map.of(
                        "words", new CommandArgumentDefinition("words", "one_or_more", true),
                        "style", new CommandArgumentDefinition("style", "option", true, List.of("plain", "bold"))),
                Set.of());

        assertEquals(List.of("say hello brave world (bold)"),
                CommandArgumentExpander.expand(mapping, List.of("hello", "brave", "world", "bold")));
    }

    @Test
    void omitsMissingOptionalVariadicPlaceholder() throws Exception {
        CommandMapping mapping = new CommandMapping(
                "optional",
                List.of("say {message}"),
                CommandPermission.ADMIN,
                Set.of(),
                Set.of(),
                Map.of("message", new CommandArgumentDefinition("message", "optional_one_or_more", false)),
                Set.of());

        assertEquals(List.of("say "), CommandArgumentExpander.expand(mapping, List.of()));
    }
}
