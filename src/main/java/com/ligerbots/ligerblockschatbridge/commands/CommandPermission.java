package com.ligerbots.ligerblockschatbridge.commands;

import java.util.Locale;

public enum CommandPermission {
    EVERYONE,
    MODERATOR,
    ADMIN;

    public static CommandPermission parse(String value) throws CommandConfigurationException {
        if (value == null || value.isBlank()) {
            throw new CommandConfigurationException("permission is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidPermission) {
            throw new CommandConfigurationException("unsupported permission: " + value, invalidPermission);
        }
    }
}
