package com.ligerbots.ligerblockschatbridge.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class CommandRegistryManager {
    private static final System.Logger LOGGER = System.getLogger(CommandRegistryManager.class.getName());
    private final Path path;
    private final AtomicReference<CommandRegistry> activeRegistry = new AtomicReference<>();

    public CommandRegistryManager(Path path) {
        this.path = path;
    }

    public void load() throws IOException, CommandConfigurationException {
        activeRegistry.set(CommandRegistryLoader.load(path));
    }

    public void load(CommandRegistry registry) {
        activeRegistry.set(registry);
    }

    public void reload() throws IOException, CommandConfigurationException {
        try {
            CommandRegistry replacement = CommandRegistryLoader.load(path);
            activeRegistry.set(replacement);
            LOGGER.log(System.Logger.Level.INFO, "Loaded Slack command registry from " + path);
        } catch (IOException | CommandConfigurationException failure) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Slack command registry reload failed; retaining the previous registry: "
                            + failure.getMessage());
            throw failure;
        }
    }

    public CommandRegistry activeRegistry() {
        CommandRegistry registry = activeRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("Slack command registry has not been loaded");
        }
        return registry;
    }

    public boolean isLoaded() {
        return activeRegistry.get() != null;
    }

    public boolean isAdministrator(String userId) {
        if (isAdministrator(activeRegistry.get(), userId)) {
            return true;
        }
        try {
            if (java.nio.file.Files.exists(path)) {
                return isAdministrator(CommandRegistryLoader.load(path), userId);
            }
        } catch (IOException | CommandConfigurationException failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Unable to validate Slack command reload authorization: " + failure.getMessage());
        }
        return false;
    }

    private static boolean isAdministrator(CommandRegistry registry, String userId) {
        if (registry == null || userId == null || userId.isBlank()) {
            return false;
        }
        return registry.admins().contains("*") || registry.admins().contains(userId)
                || registry.mappings().values().stream()
                .filter(mapping -> mapping.permission() == CommandPermission.ADMIN)
                .anyMatch(mapping -> mapping.users().contains("*") || mapping.users().contains(userId));
    }
}
