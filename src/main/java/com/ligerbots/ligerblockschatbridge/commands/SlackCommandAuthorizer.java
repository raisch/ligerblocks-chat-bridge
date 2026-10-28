package com.ligerbots.ligerblockschatbridge.commands;

import java.util.Locale;
import java.util.Set;

public final class SlackCommandAuthorizer {
    private SlackCommandAuthorizer() {
    }

    public static boolean isAuthorized(
            CommandRegistry registry,
            CommandMapping mapping,
            String userId,
            Set<String> roles) {
        if (mapping.permission() == CommandPermission.ADMIN
                && registry.admins().contains(userId)) {
            return true;
        }
        return isAuthorized(mapping, userId, roles);
    }

    public static boolean isAuthorized(
            CommandMapping mapping,
            String userId,
            Set<String> roles) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (mapping.users().contains("*") || mapping.users().contains(userId)) {
            return true;
        }
        if (mapping.permission() == CommandPermission.EVERYONE) {
            return true;
        }
        return roles.stream()
                .map(role -> role.toLowerCase(Locale.ROOT))
                .anyMatch(role -> role.equals(mapping.permission().name().toLowerCase(Locale.ROOT)));
    }

    public static boolean isAdministrator(CommandRegistry registry, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return registry.admins().contains("*") || registry.admins().contains(userId)
                || registry.mappings().values().stream()
                .filter(mapping -> mapping.permission() == CommandPermission.ADMIN)
                .anyMatch(mapping -> mapping.users().contains("*") || mapping.users().contains(userId));
    }
}
