package com.ligerbots.ligerblockschatbridge.commands;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SlackCommandHelpFormatter {
    private static final int MAX_RESPONSE_LENGTH = 2_500;

    private SlackCommandHelpFormatter() {
    }

    public static String format(CommandRegistry registry, String userId) {
        boolean administrator = SlackCommandAuthorizer.isAdministrator(registry, userId);
        StringBuilder response = new StringBuilder(
                "Available Minecraft commands (administrator-only commands are in bold):");
        Set<CommandMapping> visible = new LinkedHashSet<>(registry.mappings().values());
        List<HelpEntry> entries = new ArrayList<>();
        visible.stream()
                .filter(mapping -> SlackCommandAuthorizer.isAuthorized(registry, mapping, userId, Set.of()))
                .forEach(mapping -> entries.add(new HelpEntry(mapping.name(), mapping,
                        administrator && mapping.permission() == CommandPermission.ADMIN)));
        entries.add(new HelpEntry("help", "Show available Minecraft commands."));
        if (administrator) {
            entries.add(new HelpEntry("reload", "Reload the Minecraft command configuration.", true));
            entries.add(new HelpEntry("config", "Show the command configuration privately, excluding administrators.", true));
            entries.add(new HelpEntry("admin add <SLACK_ID> [-reload] [-list]",
                    "Add a Slack administrator.", true));
            entries.add(new HelpEntry("admin list", "List Slack administrators.", true));
            entries.add(new HelpEntry("admin del <SLACK_ID> [-reload] [-list]",
                    "Remove a Slack administrator.", true));
            entries.add(new HelpEntry("events <all|user|server> <on|off>",
                    "Enable or disable Minecraft event notifications.", true));
            entries.add(new HelpEntry("msgs <from|to> <on|off>",
                    "Enable or disable Minecraft message relay.", true));
        }
        entries.stream()
                .sorted(Comparator.comparing(HelpEntry::command))
                .forEach(entry -> {
                    if (entry.mapping() == null) {
                        append(response, entry.command(), entry.description(), entry.adminOnly());
                    } else {
                        append(response, entry.mapping(), entry.adminOnly());
                    }
                });
        return truncate(response.toString());
    }

    private static void append(StringBuilder response, CommandMapping mapping, boolean adminOnly) {
        String arguments = mapping.arguments().values().stream()
                .map(SlackCommandHelpFormatter::formatArgument)
                .collect(Collectors.joining(" "));
        String command = mapping.name() + (arguments.isBlank() ? "" : " " + arguments);
        if (!mapping.aliases().isEmpty()) {
            command += " (aliases: " + String.join(", ", mapping.aliases()) + ")";
        }
        append(response, command,
                mapping.description().isBlank() ? "No description provided." : mapping.description(), adminOnly);
    }

    private static String formatArgument(CommandArgumentDefinition argument) {
        String name = argument.name();
        return switch (argument.type().toLowerCase(java.util.Locale.ROOT)) {
            case "rest" -> argument.required() ? "<" + name + "...>" : "[" + name + "...]";
            case "one_or_more" -> "<" + name + "...>";
            case "optional_one_or_more" -> "[" + name + "...]";
            case "option" -> "<" + String.join("|", argument.options()) + ">";
            default -> argument.required() ? "<" + name + ">" : "[" + name + "]";
        };
    }

    private static void append(StringBuilder response, String command, String description, boolean adminOnly) {
        response.append("\n• ");
        if (adminOnly) {
            response.append('*');
        }
        response.append("/mc ").append(command).append(" — ").append(description);
        if (adminOnly) {
            response.append('*');
        }
    }

    private record HelpEntry(String command, String description, CommandMapping mapping, boolean adminOnly) {
        private HelpEntry(String command, CommandMapping mapping) {
            this(command, mapping.description().isBlank() ? "No description provided." : mapping.description(),
                    mapping, false);
        }

        private HelpEntry(String command, CommandMapping mapping, boolean adminOnly) {
            this(command, mapping.description().isBlank() ? "No description provided." : mapping.description(),
                    mapping, adminOnly);
        }

        private HelpEntry(String command, String description) {
            this(command, description, null, false);
        }

        private HelpEntry(String command, String description, boolean adminOnly) {
            this(command, description, null, adminOnly);
        }
    }

    private static String truncate(String response) {
        return response.length() <= MAX_RESPONSE_LENGTH
                ? response
                : response.substring(0, MAX_RESPONSE_LENGTH - 1) + "…";
    }
}
