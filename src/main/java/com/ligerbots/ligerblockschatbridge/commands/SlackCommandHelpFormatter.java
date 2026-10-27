package com.ligerbots.ligerblockschatbridge.commands;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class SlackCommandHelpFormatter {
    private static final int MAX_RESPONSE_LENGTH = 2_500;

    private SlackCommandHelpFormatter() {
    }

    public static String format(CommandRegistry registry, String userId) {
        StringBuilder response = new StringBuilder("Available Minecraft commands:");
        Set<CommandMapping> visible = new LinkedHashSet<>(registry.mappings().values());
        visible.stream()
                .filter(mapping -> SlackCommandAuthorizer.isAuthorized(registry, mapping, userId, Set.of()))
                .sorted(Comparator.comparing(CommandMapping::name))
                .forEach(mapping -> append(response, mapping));
        append(response, "help", "Show available Minecraft commands.");
        return truncate(response.toString());
    }

    private static void append(StringBuilder response, CommandMapping mapping) {
        String arguments = mapping.arguments().values().stream()
                .map(SlackCommandHelpFormatter::formatArgument)
                .collect(Collectors.joining(" "));
        append(response, mapping.name() + (arguments.isBlank() ? "" : " " + arguments),
                mapping.description().isBlank() ? "No description provided." : mapping.description());
        if (!mapping.aliases().isEmpty()) {
            response.append(" (aliases: ")
                    .append(String.join(", ", mapping.aliases()))
                    .append(')');
        }

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

    private static void append(StringBuilder response, String command, String description) {
        response.append("\n• /mc ").append(command).append(" — ").append(description);
    }

    private static String truncate(String response) {
        return response.length() <= MAX_RESPONSE_LENGTH
                ? response
                : response.substring(0, MAX_RESPONSE_LENGTH - 1) + "…";
    }
}
