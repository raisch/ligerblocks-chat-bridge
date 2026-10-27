package com.ligerbots.ligerblockschatbridge.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackCommandHelpFormatterTest {
    @Test
    void showsAuthorizedCommandsAndHidesAdminCommands() throws Exception {
        CommandRegistry registry = CommandRegistryLoader.parse("""
                {
                  "admins": ["U123"],
                  "commands": {
                    "players": {
                      "minecraft": "list",
                      "permission": "everyone",
                      "description": "Show online players"
                    },
                    "secret": {
                      "minecraft": "list",
                      "permission": "admin",
                      "description": "Admin-only command",
                      "arguments": {
                        "player": {"type": "minecraft_username", "required": true}
                      }
                    }
                  }
                }
                """);

        String publicHelp = SlackCommandHelpFormatter.format(registry, "U999");
        String adminHelp = SlackCommandHelpFormatter.format(registry, "U123");

        assertTrue(publicHelp.contains("/mc players — Show online players"));
        assertTrue(!publicHelp.contains("/mc secret"));
        assertTrue(adminHelp.contains("/mc secret <player> — Admin-only command"));
        assertTrue(adminHelp.contains("/mc help — Show available Minecraft commands."));
    }
}
