package com.ligerbots.ligerblockschatbridge.commands;

import java.util.List;
import java.util.Optional;

public final class SlackCommandParser {
    private SlackCommandParser() {
    }

    public static Optional<ParsedCommand> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        String commandName = tokens.removeFirst().toLowerCase();
        if (commandName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedCommand(commandName, tokens));
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new java.util.ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (char character : text.trim().toCharArray()) {
            if (escaped) {
                token.append(character);
                escaped = false;
            } else if (character == '\\' && quote != '\'') {
                escaped = true;
            } else if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                } else {
                    token.append(character);
                }
            } else if (character == '\'' || character == '"') {
                quote = character;
            } else if (Character.isWhitespace(character)) {
                if (!token.isEmpty()) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(character);
            }
        }
        if (escaped || quote != 0) {
            return List.of();
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    public record ParsedCommand(String name, List<String> arguments) {
        public ParsedCommand {
            arguments = List.copyOf(arguments);
        }
    }
}
