package com.ligerbots.ligerblockschatbridge.commands;

import org.junit.jupiter.api.Test;

import java.util.Optional;

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
        assertEquals("players", registry.find("online").orElseThrow().name());
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
