package com.ligerbots.ligerblockschatbridge.commands;

import java.util.Locale;

public enum CommandResponseMode {
    PRIVATE,
    CHANNEL;

    public static CommandResponseMode parse(String value) throws CommandConfigurationException {
        if (value == null || value.isBlank()) {
            return PRIVATE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidMode) {
            throw new CommandConfigurationException("unsupported response mode: " + value, invalidMode);
        }
    }
}
