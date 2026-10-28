package com.ligerbots.ligerblockschatbridge.commands;

public final class CommandConfigurationException extends Exception {
    public CommandConfigurationException(String message) {
        super(message);
    }

    public CommandConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
