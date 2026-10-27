package com.ligerbots.ligerblockschatbridge.commands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackCommandAuthorizerTest {
    @Test
    void everyoneAndExplicitUsersAreAuthorized() {
        var everyone = mapping(CommandPermission.EVERYONE, Set.of());
        var explicit = mapping(CommandPermission.ADMIN, Set.of("U123"));

        assertTrue(SlackCommandAuthorizer.isAuthorized(everyone, "U999", Set.of()));
        assertTrue(SlackCommandAuthorizer.isAuthorized(explicit, "U123", Set.of()));
        assertFalse(SlackCommandAuthorizer.isAuthorized(explicit, "U999", Set.of()));
    }

    @Test
    void roleAuthorizationIsExplicit() {
        var mapping = mapping(CommandPermission.ADMIN, Set.of());

        assertTrue(SlackCommandAuthorizer.isAuthorized(mapping, "U123", Set.of("admin")));
        assertFalse(SlackCommandAuthorizer.isAuthorized(mapping, "U123", Set.of("moderator")));
    }

    @Test
    void reloadRequiresAnExplicitAdminUser() throws Exception {
        CommandRegistry registry = CommandRegistryLoader.parse("""
                {"admins":["U123"],"commands":{
                  "whitelist":{"minecraft":"whitelist list","permission":"admin"}
                }}
                """);

        assertTrue(SlackCommandAuthorizer.isAdministrator(registry, "U123"));
        assertFalse(SlackCommandAuthorizer.isAdministrator(registry, "U999"));
    }

    private static CommandMapping mapping(CommandPermission permission, Set<String> users) {
        return new CommandMapping("test", List.of("list"), permission, users, Set.of(), Map.of(), Set.of());
    }
}
