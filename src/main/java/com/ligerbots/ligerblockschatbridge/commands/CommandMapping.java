package com.ligerbots.ligerblockschatbridge.commands;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record CommandMapping(
        String name,
        List<String> minecraftCommands,
        CommandPermission permission,
        Set<String> users,
        Set<String> roles,
        Map<String, CommandArgumentDefinition> arguments,
        Set<String> aliases,
        String description) {
    public CommandMapping(
            String name,
            List<String> minecraftCommands,
            CommandPermission permission,
            Set<String> users,
            Set<String> roles,
            Map<String, CommandArgumentDefinition> arguments,
            Set<String> aliases) {
        this(name, minecraftCommands, permission, users, roles, arguments, aliases, "");
    }

    public CommandMapping {
        minecraftCommands = List.copyOf(minecraftCommands);
        users = Set.copyOf(users);
        roles = Set.copyOf(roles);
        arguments = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        aliases = Set.copyOf(aliases);
        description = description == null ? "" : description.trim();
    }

    public boolean isMacro() {
        return minecraftCommands.size() > 1;
    }
}
