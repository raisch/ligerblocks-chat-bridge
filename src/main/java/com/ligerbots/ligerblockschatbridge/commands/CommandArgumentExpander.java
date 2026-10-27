package com.ligerbots.ligerblockschatbridge.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandArgumentExpander {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-z0-9_-]*)}");
    private static final int MAX_ARGUMENTS = 16;
    private static final int MAX_ARGUMENT_LENGTH = 64;

    private CommandArgumentExpander() {
    }

    public static List<String> expand(CommandMapping mapping, List<String> values)
            throws CommandConfigurationException {
        if (values.size() > MAX_ARGUMENTS) {
            throw invalid("too many arguments");
        }
        List<String> orderedValues = List.copyOf(values);
        Map<String, CommandArgumentDefinition> definitions = mapping.arguments();
        boolean hasVariadicArgument = definitions.values().stream()
                .anyMatch(definition -> isVariadic(definition.type()));
        if (orderedValues.size() > definitions.size() && !hasVariadicArgument) {
            throw invalid("too many arguments for " + mapping.name());
        }

        Map<String, String> resolved = new java.util.LinkedHashMap<>();
        int index = 0;
        for (CommandArgumentDefinition definition : definitions.values()) {
            String type = definition.type().toLowerCase(java.util.Locale.ROOT);
            if ("rest".equals(type)) {
                if (index >= orderedValues.size()) {
                    if (definition.required()) {
                        throw invalid("missing required argument " + definition.name());
                    }
                    continue;
                }
                String value = String.join(" ", orderedValues.subList(index, orderedValues.size()));
                validate(definition, value);
                resolved.put(definition.name(), value);
                index = orderedValues.size();
                continue;
            }
            if ("one_or_more".equals(type) || "optional_one_or_more".equals(type)) {
                int remainingDefinitions = definitions.size() - resolved.size() - 1;
                int availableValues = orderedValues.size() - index;
                int valueCount = Math.max(0, availableValues - remainingDefinitions);
                if (valueCount == 0) {
                    if ("one_or_more".equals(type)) {
                        throw invalid("missing required argument " + definition.name());
                    }
                    continue;
                }
                String value = String.join(" ", orderedValues.subList(index, index + valueCount));
                validate(definition, value);
                resolved.put(definition.name(), value);
                index += valueCount;
                continue;
            }
            if (index >= orderedValues.size()) {
                if (isRequired(definition, resolved)) {
                    throw invalid("missing required argument " + definition.name());
                }
                continue;
            }
            if (definition.requiredWhenArgument() != null && !isRequired(definition, resolved)) {
                throw invalid("argument " + definition.name() + " is not valid for "
                        + definition.requiredWhenArgument() + "="
                        + resolved.get(definition.requiredWhenArgument()));
            }
            String value = orderedValues.get(index++);
            validate(definition, value);
            resolved.put(definition.name(), value);
        }

        List<String> commands = new ArrayList<>(mapping.minecraftCommands().size());
        for (String template : mapping.minecraftCommands()) {
            Matcher matcher = PLACEHOLDER.matcher(template);
            StringBuffer expanded = new StringBuffer();
            while (matcher.find()) {
                String value = resolved.get(matcher.group(1));
                if (value == null) {
                    CommandArgumentDefinition definition = definitions.get(matcher.group(1));
                    if (definition == null || definition.required()) {
                        throw invalid("missing argument " + matcher.group(1));
                    }
                    value = "";
                }
                matcher.appendReplacement(expanded, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(expanded);
            commands.add(expanded.toString());
        }
        return List.copyOf(commands);
    }

    private static void validate(CommandArgumentDefinition definition, String value)
            throws CommandConfigurationException {
        if (value == null || value.isBlank() || value.length() > MAX_ARGUMENT_LENGTH
                || value.indexOf('\0') >= 0) {
            throw invalid("invalid argument " + definition.name());
        }

        if ("minecraft_username".equalsIgnoreCase(definition.type()) && !USERNAME.matcher(value).matches()) {
            throw invalid("invalid Minecraft username");
        }
        if ("option".equalsIgnoreCase(definition.type())
                && definition.options().stream().noneMatch(option -> option.equalsIgnoreCase(value))) {
            throw invalid("invalid option for " + definition.name());
        }
        if ("word".equalsIgnoreCase(definition.type()) && !value.matches("[A-Za-z0-9_.:-]+")) {
            throw invalid("invalid argument " + definition.name());
        }
    }

    private static boolean isVariadic(String type) {
        return "rest".equalsIgnoreCase(type)
                || "one_or_more".equalsIgnoreCase(type)
                || "optional_one_or_more".equalsIgnoreCase(type);
    }

    private static boolean isRequired(
            CommandArgumentDefinition definition,
            Map<String, String> resolved) {
        if (definition.required()) {
            return true;
        }
        if (definition.requiredWhenArgument() == null) {
            return false;
        }
        String controllingValue = resolved.get(definition.requiredWhenArgument());
        return controllingValue != null && definition.requiredWhenValues().stream()
                .anyMatch(value -> value.equalsIgnoreCase(controllingValue));
    }

    private static CommandConfigurationException invalid(String message) {
        return new CommandConfigurationException(message);
    }
}
