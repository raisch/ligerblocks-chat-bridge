package com.ligerbots.ligerblockschatbridge.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    public String addAdministrator(String userId) throws IOException, CommandConfigurationException {
        CommandRegistry current = CommandRegistryLoader.load(path);
        if (current.admins().contains(userId)) {
            return "Slack user " + userId + " is already an administrator.";
        }

        String json = Files.readString(path, StandardCharsets.UTF_8);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                JsonAdminsEditor.add(json, userId),
                StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
        return "Added Slack user " + userId + " to administrators.";
    }

    public String administrators() throws IOException, CommandConfigurationException {
        return String.join(", ", CommandRegistryLoader.load(path).admins());
    }

    public String removeAdministrator(String userId) throws IOException, CommandConfigurationException {
        CommandRegistry current = CommandRegistryLoader.load(path);
        if (!current.admins().contains(userId)) {
            return "Slack user " + userId + " is not an administrator.";
        }
        if (current.admins().size() <= 1) {
            throw new CommandConfigurationException("cannot remove the final administrator");
        }

        String json = Files.readString(path, StandardCharsets.UTF_8);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                JsonAdminsEditor.remove(json, userId),
                StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
        return "Removed Slack user " + userId + " from administrators.";
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
