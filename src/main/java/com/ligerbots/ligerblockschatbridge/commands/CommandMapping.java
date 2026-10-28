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
        String description,
        CommandResponseMode responseMode,
        Integer minecraftPermission,
        boolean broadcastAsSlack) {
    public CommandMapping(
            String name,
            List<String> minecraftCommands,
            CommandPermission permission,
            Set<String> users,
            Set<String> roles,
            Map<String, CommandArgumentDefinition> arguments,
            Set<String> aliases) {
        this(name, minecraftCommands, permission, users, roles, arguments, aliases, "",
                CommandResponseMode.PRIVATE, null, false);
    }

    public CommandMapping(
            String name,
            List<String> minecraftCommands,
            CommandPermission permission,
            Set<String> users,
            Set<String> roles,
            Map<String, CommandArgumentDefinition> arguments,
            Set<String> aliases,
            String description) {
        this(name, minecraftCommands, permission, users, roles, arguments, aliases, description,
                CommandResponseMode.PRIVATE, null, false);
    }

    public CommandMapping {
        minecraftCommands = List.copyOf(minecraftCommands);
        users = Set.copyOf(users);
        roles = Set.copyOf(roles);
        arguments = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        aliases = Set.copyOf(aliases);
        description = description == null ? "" : description.trim();
        responseMode = responseMode == null ? CommandResponseMode.PRIVATE : responseMode;
        if (minecraftPermission != null && (minecraftPermission < 0 || minecraftPermission > 4)) {
            throw new IllegalArgumentException("minecraftPermission must be between 0 and 4");
        }
    }

    public boolean isMacro() {
        return minecraftCommands.size() > 1;
    }
}
