package com.ligerbots.ligerblockschatbridge.commands;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryLoaderTest {
    @Test
    void loadsMappingsAliasesArgumentsAndMacros() throws Exception {
        CommandRegistry registry = CommandRegistryLoader.parse("""
                {
                  "commands": {
                    "players": {
                      "minecraft": "list",
                      "permission": "everyone",
                      "description": "Show online players",
                      "aliases": ["online"]
                    },
                    "announce": {
                      "minecraft": "say {message}",
                      "permission": "everyone",
                      "minecraftPermission": 2,
                      "arguments": {
                        "message": {"type": "rest", "required": true}
                      }
                    },
                    "morning": {
                      "minecraft": ["time set day", "weather clear"],
                      "permission": "moderator"
                    },
                    "addplayer": {
                      "minecraft": "whitelist add {player}",
                      "permission": "admin",
                      "arguments": {
                        "player": {"type": "minecraft_username", "required": true}
                      }
                    }
                  }
                }
                """);

        assertEquals("list", registry.find("players").orElseThrow().minecraftCommands().getFirst());
        assertEquals("Show online players", registry.find("players").orElseThrow().description());
        assertEquals(CommandResponseMode.PRIVATE, registry.find("players").orElseThrow().responseMode());
        assertEquals("players", registry.find("online").orElseThrow().name());
        assertEquals(2, registry.find("announce").orElseThrow().minecraftPermission());
        assertEquals(2, registry.find("morning").orElseThrow().minecraftCommands().size());
        assertEquals("minecraft_username",
                registry.find("addplayer").orElseThrow().arguments().get("player").type());
    }

    @Test
    void rejectsUndefinedPlaceholdersAndDuplicateNames() {
        assertThrows(CommandConfigurationException.class, () -> CommandRegistryLoader.parse("""
                {"commands":{"players":{"minecraft":"list {missing}","permission":"everyone"}}}
                """));
        assertThrows(CommandConfigurationException.class, () -> CommandRegistryLoader.parse("""
                {"commands":{
                  "players":{"minecraft":"list","permission":"everyone","aliases":["online"]},
                  "other":{"minecraft":"list","permission":"everyone","aliases":["online"]}
                }}
                """));
    }

    @Test
    void rejectsMissingPermissionAndInvalidCommandNames() {
        assertThrows(CommandConfigurationException.class, () -> CommandRegistryLoader.parse("""
                {"commands":{"players":{"minecraft":"list"}}}
                """));
        assertThrows(CommandConfigurationException.class, () -> CommandRegistryLoader.parse("""
                {"commands":{"Players":{"minecraft":"list","permission":"everyone"}}}
                """));
        assertThrows(CommandConfigurationException.class, () -> CommandRegistryLoader.parse("""
                {"commands":{"players":{
                  "minecraft":"list","permission":"everyone","response":"broadcast"
                }}}
                """));
        assertThrows(CommandConfigurationException.class, () -> CommandRegistryLoader.parse("""
                {"commands":{"players":{
                  "minecraft":"list","permission":"everyone","minecraftPermission":5
                }}}
                """));
    }

    @Test
    void loadsChannelResponseMode() throws Exception {
        CommandRegistry registry = CommandRegistryLoader.parse("""
                {"commands":{"announce":{
                  "minecraft":"say hello","permission":"admin","response":"channel"
                }}}
                """);

        assertEquals(CommandResponseMode.CHANNEL, registry.find("announce").orElseThrow().responseMode());
    }

    @Test
    void stripsCommentsBeforeParsingWithoutChangingStringValues() throws Exception {
        CommandRegistry registry = CommandRegistryLoader.parse("""
                {
                  // Public command
                  "commands": {
                    "announce": {
                      "minecraft": "say https://example.test/a/*b*/",
                      /*
                       * Preserve comment markers inside strings: // and /*
                       */
                      "permission": "everyone",
                      "description": "Use // and /* markers literally"
                    }
                  }
                }
                """);

        CommandMapping mapping = registry.find("announce").orElseThrow();
        assertEquals("say https://example.test/a/*b*/", mapping.minecraftCommands().getFirst());
        assertEquals("Use // and /* markers literally", mapping.description());
    }

    @Test
    void rejectsUnterminatedBlockComments() {
        assertThrows(CommandConfigurationException.class, () -> CommandRegistryLoader.parse("""
                {
                  "commands": {
                    "players": {
                      "minecraft": "list",
                      "permission": "everyone"
                    }
                  }
                /* missing terminator
                """));
    }

    @Test
    void removesAdministratorsFromConfigurationView() throws Exception {
        String view = CommandRegistryLoader.withoutAdministrators("""
                {
                  "admins": ["U123"],
                  "commands": {
                    "players": {"minecraft": "list", "permission": "everyone"}
                  }
                }
                """);

        assertEquals(false, view.contains("\"admins\""));
        assertEquals("players", CommandRegistryLoader.parse(view).find("players").orElseThrow().name());
    }

    @Test
    void loadsConditionalArgumentRequirements() throws Exception {
        CommandRegistry registry = CommandRegistryLoader.parse("""
                {"commands":{"whitelist":{
                  "minecraft":"whitelist {action} {player}",
                  "permission":"admin",
                  "arguments":{
                    "action":{"type":"option","options":["list","add","del"]},
                    "player":{
                      "type":"minecraft_username",
                      "required":false,
                      "requiredWhen":{"argument":"action","values":["add","del"]}
                    }
                  }
                }}}
                """);

        CommandArgumentDefinition player = registry.find("whitelist").orElseThrow()
                .arguments().get("player");
        assertEquals("action", player.requiredWhenArgument());
        assertEquals(List.of("add", "del"), player.requiredWhenValues());
    }

    @Test
    void resolvesUniquePrefixesButRejectsAmbiguousPrefixes() throws Exception {
                CommandRegistry registry = CommandRegistryLoader.parse("""
                        {"commands":{
                          "day":{"minecraft":"time set day","permission":"admin"},
                          "daytime":{"minecraft":"time query daytime","permission":"everyone"},
                          "night":{"minecraft":"time set night","permission":"admin"},
                          "noon":{"minecraft":"time set noon","permission":"admin"}
                        }}
                        """);

                assertEquals("night", registry.resolve("ni").mapping().name());
                assertEquals("day", registry.resolve("day").mapping().name());
                assertTrue(registry.resolve("d").isAmbiguous());
                assertTrue(registry.resolve("n").isAmbiguous());
                assertTrue(!registry.resolve("missing").isMatch());
    }

    @Test
    void rejectsArgumentsAfterAvariadicArgument() {
                assertThrows(CommandConfigurationException.class, () -> CommandRegistryLoader.parse("""
                        {"commands":{"invalid":{
                          "minecraft":"say {message} {player}",
                          "permission":"everyone",
                          "arguments":{
                            "message":{"type":"rest","required":true},
                            "player":{"type":"minecraft_username","required":true}
                          }
                        }}}
                        """));
    }
}
