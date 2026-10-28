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

        assertTrue(adminHelp.startsWith(
                "Available Minecraft commands (administrator-only commands are in bold):"));
        assertTrue(publicHelp.contains("/mc players — Show online players"));
        assertTrue(!publicHelp.contains("/mc secret"));
        assertTrue(adminHelp.contains("*/mc secret <player> — Admin-only command*"));
        assertTrue(adminHelp.contains("/mc help — Show available Minecraft commands."));
        assertTrue(adminHelp.contains("*/mc reload — Reload the Minecraft command configuration.*"));
        assertTrue(adminHelp.contains("*/mc config — Show the command configuration privately, excluding administrators.*"));
        assertTrue(adminHelp.contains("*/mc admin add <SLACK_ID> [-reload] [-list]"));
        assertTrue(!publicHelp.contains("/mc reload"));
        assertTrue(!publicHelp.contains("/mc admin add"));
        assertTrue(adminHelp.indexOf("/mc admin list") < adminHelp.indexOf("/mc config"));
        assertTrue(adminHelp.indexOf("/mc config") < adminHelp.indexOf("/mc help"));
        assertTrue(adminHelp.indexOf("/mc help") < adminHelp.indexOf("/mc players"));
    }
}
