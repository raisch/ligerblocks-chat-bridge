package com.ligerbots.ligerblockschatbridge.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandRegistryLoader {
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-z][a-z0-9_-]*)}");
    private static final int MAX_MAPPINGS = 128;
    private static final int MAX_MACRO_COMMANDS = 16;

    private CommandRegistryLoader() {
    }

    public static CommandRegistry load(Path path) throws IOException, CommandConfigurationException {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return parse(json);
        } catch (JsonParseException | IllegalStateException parseFailure) {
            throw new CommandConfigurationException("invalid command configuration: " + path, parseFailure);
        }
    }

    public static CommandRegistry loadResource(String resourceName)
            throws IOException, CommandConfigurationException {
        try (InputStream resource = CommandRegistryLoader.class.getResourceAsStream(resourceName)) {
            if (resource == null) {
                throw new IOException("missing command configuration resource: " + resourceName);
            }
            return parse(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static CommandRegistry parse(String json) throws CommandConfigurationException {
        if (json == null || json.isBlank()) {
            throw new CommandConfigurationException("command configuration is empty");
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException parseFailure) {
            throw new CommandConfigurationException("command configuration must be a JSON object", parseFailure);
        }
        JsonObject commands = objectProperty(root, "commands");
        if (commands == null || commands.entrySet().isEmpty()) {
            throw new CommandConfigurationException("commands must contain at least one mapping");
        }
        if (commands.size() > MAX_MAPPINGS) {
            throw new CommandConfigurationException("commands may contain at most " + MAX_MAPPINGS + " mappings");
        }
        Set<String> admins = normalizedSet(root, "admins");

        Map<String, CommandMapping> mappings = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : commands.entrySet()) {
            String name = normalizeName(entry.getKey(), "command");
            if (!entry.getValue().isJsonObject()) {
                throw new CommandConfigurationException("command " + name + " must be an object");
            }
            CommandMapping mapping = parseMapping(name, entry.getValue().getAsJsonObject());
            addName(mappings, name, mapping);
            for (String alias : mapping.aliases()) {
                addName(mappings, alias, mapping);
            }
        }
        return new CommandRegistry(mappings, admins);
    }

    private static CommandMapping parseMapping(String name, JsonObject object)
            throws CommandConfigurationException {
        List<String> minecraftCommands = stringList(object, "minecraft");
        if (minecraftCommands.isEmpty()) {
            throw new CommandConfigurationException("command " + name + " must define minecraft");
        }
        if (minecraftCommands.size() > MAX_MACRO_COMMANDS) {
            throw new CommandConfigurationException("command " + name + " has too many macro commands");
        }
        for (String command : minecraftCommands) {
            if (command.isBlank() || command.startsWith("/")) {
                throw new CommandConfigurationException(
                        "command " + name + " contains an invalid Minecraft command");
            }
        }

        CommandPermission permission = CommandPermission.parse(stringProperty(object, "permission"));
        Set<String> users = normalizedSet(object, "users");
        Set<String> roles = normalizedSet(object, "roles");
        Set<String> aliases = normalizedNames(object, "aliases");
        String description = stringProperty(object, "description");
        Map<String, CommandArgumentDefinition> arguments = parseArguments(name, objectProperty(object, "arguments"));
        validatePlaceholders(name, minecraftCommands, arguments);
        return new CommandMapping(name, minecraftCommands, permission, users, roles, arguments, aliases, description);
    }

    private static Map<String, CommandArgumentDefinition> parseArguments(String command, JsonObject arguments)
            throws CommandConfigurationException {
        if (arguments == null) {
            return Map.of();
        }
        Map<String, CommandArgumentDefinition> definitions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : arguments.entrySet()) {
            String name = normalizeName(entry.getKey(), "argument");
            if (!entry.getValue().isJsonObject()) {
                throw new CommandConfigurationException(
                        "argument " + name + " for command " + command + " must be an object");
            }
            JsonObject definition = entry.getValue().getAsJsonObject();
            String type = stringProperty(definition, "type");
            if (type == null || type.isBlank()) {
                throw new CommandConfigurationException(
                        "argument " + name + " for command " + command + " must define type");
            }
            if (!isSupportedType(type)) {
                throw new CommandConfigurationException(
                        "unsupported argument type " + type + " for " + name + " in command " + command);
            }
            boolean required = booleanProperty(definition, "required", true);
            List<String> options = stringList(definition, "options");
            if ("option".equalsIgnoreCase(type) && options.isEmpty()) {
                throw new CommandConfigurationException(
                        "option argument " + name + " for command " + command + " must define options");
            }
            if (!"option".equalsIgnoreCase(type) && !options.isEmpty()) {
                throw new CommandConfigurationException(
                        "options are only supported for option arguments: " + name);
            }
            definitions.put(name, new CommandArgumentDefinition(name, type, required, options));
        }
        validateVariadicArguments(command, definitions);
        return definitions;
    }

    private static void validateVariadicArguments(
            String command,
            Map<String, CommandArgumentDefinition> definitions) throws CommandConfigurationException {
        boolean variadic = false;
        List<CommandArgumentDefinition> ordered = new ArrayList<>(definitions.values());
        for (int index = 0; index < ordered.size(); index++) {
            CommandArgumentDefinition definition = ordered.get(index);
            boolean isVariadic = isVariadic(definition.type());
            if (variadic && !isOption(definition)) {
                throw new CommandConfigurationException(
                        "variadic argument " + command + " must be the final argument");
            }
            if (isVariadic && index < ordered.size() - 1
                    && !(isOneOrMore(definition) && isOption(ordered.get(index + 1))
                    && index == ordered.size() - 2)) {
                throw new CommandConfigurationException(
                        "only one_or_more followed by a final option is supported for command " + command);
            }
            variadic = isVariadic;
        }
    }

    private static boolean isVariadic(String type) {
        return "rest".equalsIgnoreCase(type)
                || "one_or_more".equalsIgnoreCase(type)
                || "optional_one_or_more".equalsIgnoreCase(type);
    }

    private static boolean isSupportedType(String type) {
        return "word".equalsIgnoreCase(type)
                || "minecraft_username".equalsIgnoreCase(type)
                || "option".equalsIgnoreCase(type)
                || isVariadic(type);
    }

    private static boolean isOption(CommandArgumentDefinition definition) {
        return "option".equalsIgnoreCase(definition.type());
    }

    private static boolean isOneOrMore(CommandArgumentDefinition definition) {
        return "one_or_more".equalsIgnoreCase(definition.type());
    }

    private static void validatePlaceholders(
            String command,
            List<String> minecraftCommands,
            Map<String, CommandArgumentDefinition> arguments) throws CommandConfigurationException {
        for (String minecraftCommand : minecraftCommands) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(minecraftCommand);
            while (matcher.find()) {
                if (!arguments.containsKey(matcher.group(1))) {
                    throw new CommandConfigurationException(
                            "command " + command + " references undefined argument " + matcher.group(1));
                }
            }
        }
    }

    private static void addName(
            Map<String, CommandMapping> mappings,
            String name,
            CommandMapping mapping) throws CommandConfigurationException {
        if (mappings.putIfAbsent(name, mapping) != null) {
            throw new CommandConfigurationException("duplicate command or alias: " + name);
        }
    }

    private static String normalizeName(String value, String kind) throws CommandConfigurationException {
        if (value == null || !NAME_PATTERN.matcher(value).matches()) {
            throw new CommandConfigurationException("invalid " + kind + " name: " + value);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static List<String> stringList(JsonObject object, String property)
            throws CommandConfigurationException {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                values.add(stringValue(element, property));
            }
        } else {
            values.add(stringValue(value, property));
        }
        return values;
    }

    private static Set<String> normalizedNames(JsonObject object, String property)
            throws CommandConfigurationException {
        Set<String> values = new LinkedHashSet<>();
        for (String value : stringList(object, property)) {
            values.add(normalizeName(value, property));
        }
        return values;
    }

    private static Set<String> normalizedSet(JsonObject object, String property)
            throws CommandConfigurationException {
        return new LinkedHashSet<>(stringList(object, property));
    }

    private static String stringProperty(JsonObject object, String property) {
        JsonElement value = object.get(property);
        return value == null || value.isJsonNull() ? null : stringValue(value, property);
    }

    private static boolean booleanProperty(JsonObject object, String property, boolean defaultValue) {
        JsonElement value = object.get(property);
        return value == null || value.isJsonNull() ? defaultValue : value.getAsBoolean();
    }

    private static JsonObject objectProperty(JsonObject object, String property) {
        JsonElement value = object.get(property);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String stringValue(JsonElement value, String property) {
        return value.isJsonPrimitive() ? value.getAsString().trim() : "";
    }
}
