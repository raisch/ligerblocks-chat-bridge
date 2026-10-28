package com.ligerbots.ligerblockschatbridge.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class MinecraftCommandExecutor {
    private static final int MAX_OUTPUT_LENGTH = 2_000;
    private final MinecraftServer server;
    private final Supplier<CommandRegistry> registry;
    private final Consumer<CommandExecutionResult> resultConsumer;

    public MinecraftCommandExecutor(
            MinecraftServer server,
            Supplier<CommandRegistry> registry,
            Consumer<CommandExecutionResult> resultConsumer) {
        this.server = server;
        this.registry = registry;
        this.resultConsumer = resultConsumer;
    }

    public void execute(SlackCommandRequest request) {
        CommandMapping mapping = registry.get().find(request.commandName()).orElse(null);
        if (mapping == null) {
            publish(request, "Command is no longer configured.");
            return;
        }
        final List<String> commands;
        try {
            commands = CommandArgumentExpander.expand(mapping, request.arguments());
        } catch (CommandConfigurationException invalid) {
            publish(request, "Invalid command arguments: " + invalid.getMessage());
            return;
        }

        server.execute(() -> executeOnServerThread(request, mapping, commands));
    }

    private void executeOnServerThread(
            SlackCommandRequest request,
            CommandMapping mapping,
            List<String> commands) {
        if (!server.isRunning()) {
            publish(request, "Minecraft server is not available.");
            return;
        }

        if (mapping.broadcastAsSlack()) {
            broadcastAsSlack(request, commands);
            publish(request, "Command completed (result 1)");
            return;
        }

        List<String> feedback = Collections.synchronizedList(new ArrayList<>());
        CommandSource output = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                append(feedback, message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }

        };
        CommandResult result = new CommandResult();
        CommandSourceStack source = server.createCommandSourceStack()
                .withSource(output)
                .withPermission(permissionLevelFor(mapping))
                .withCallback((success, value) -> {
                    result.success |= success;
                    result.value = value;
                });

        for (String command : commands) {
            try {
                int value = server.getCommands().getDispatcher().execute(command, source);
                result.success |= value >= 0;
                result.value = value;
                if (!feedback.isEmpty()) {
                    break;
                }
            } catch (CommandSyntaxException failure) {
                append(feedback, failure.getRawMessage().getString());
                break;
            } catch (RuntimeException failure) {
                append(feedback, "Command execution failed.");
                break;
            }
        }

        if (commands.stream().anyMatch(command -> command.startsWith("spark "))) {
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
                    .execute(() -> publishCommandResult(request, result, feedback));
        } else {
            publishCommandResult(request, result, feedback);
        }
    }

    private void broadcastAsSlack(SlackCommandRequest request, List<String> commands) {
        String identity = request.userName();
        if (identity == null || identity.isBlank()) {
            identity = request.userId();
        }
        String message = commands.getFirst();
        int separator = message.indexOf(' ');
        message = separator >= 0 ? message.substring(separator + 1) : "";
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Slack] <" + identity + "> " + message), false);
    }

    private void publishCommandResult(
            SlackCommandRequest request,
            CommandResult result,
            List<String> feedback) {
        String status = result.success ? "Command completed" : "Command failed";
        if (result.value != null) {
            status += " (result " + result.value + ")";
        }
        synchronized (feedback) {
            if (!feedback.isEmpty()) {
                status += ": " + String.join(" | ", feedback);
            }
        }
        publish(request, truncate(status));
    }

    private static int permissionLevelFor(CommandMapping mapping) {
        if (mapping.minecraftPermission() != null) {
            return mapping.minecraftPermission();
        }
        return switch (mapping.permission()) {
            case EVERYONE -> 0;
            case MODERATOR -> 2;
            case ADMIN -> 4;
        };
    }

    private void publish(SlackCommandRequest request, String text) {
        resultConsumer.accept(new CommandExecutionResult(request, truncate(text)));
    }

    private static void append(List<String> feedback, String message) {
        if (message == null || message.isBlank() || feedback.size() >= 16) {
            return;
        }
        feedback.add(truncate(message));
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_OUTPUT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_OUTPUT_LENGTH - 1) + "…";
    }

    private static final class CommandResult {
        private volatile boolean success;
        private volatile Integer value;
    }

    public record CommandExecutionResult(SlackCommandRequest request, String text) {
    }
}
