package com.ligerbots.ligerblockschatbridge.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandRegistryManagerTest {
    @Test
    void invalidReloadLeavesPreviousRegistryActive() throws Exception {
        var path = Files.createTempFile("slack-commands", ".json");
        try {
            Files.writeString(path, """
                    {"commands":{"players":{"minecraft":"list","permission":"everyone"}}}
                    """);
            CommandRegistryManager manager = new CommandRegistryManager(path);
            manager.load();

            Files.writeString(path, """
                    {"commands":{"players":{"minecraft":"list"}}}
                    """);
            assertThrows(CommandConfigurationException.class, manager::reload);
            assertEquals("players", manager.activeRegistry().find("players").orElseThrow().name());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void validReloadPublishesAnAtomicReplacementForConcurrentReaders() throws Exception {
        var path = Files.createTempFile("slack-commands", ".json");
        var executor = Executors.newFixedThreadPool(2);
        try {
            Files.writeString(path, """
                    {"commands":{"players":{"minecraft":"list","permission":"everyone"}}}
                    """);
            CommandRegistryManager manager = new CommandRegistryManager(path);
            manager.load();
            CountDownLatch started = new CountDownLatch(1);
            var reader = executor.submit(() -> {
                started.countDown();
                for (int i = 0; i < 100; i++) {
                    if (manager.activeRegistry().mappings().isEmpty()) {
                        throw new AssertionError("registry snapshot must never be empty");
                    }
                }
            });
            started.await();
            Files.writeString(path, """
                    {"commands":{"daytime":{"minecraft":"time query daytime","permission":"everyone"}}}
                    """);
            manager.reload();
            reader.get();
            assertEquals("daytime", manager.activeRegistry().find("daytime").orElseThrow().name());
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(path);
        }
    }

    @Test
    void allowsConfiguredAdminToBootstrapReloadBeforeInitialLoad() throws Exception {
        var path = Files.createTempFile("slack-commands", ".json");
        try {
            Files.writeString(path, """
                    {"admins":["U123"],"commands":{
                      "players":{"minecraft":"list","permission":"everyone"}
                    }}
                    """);
            CommandRegistryManager manager = new CommandRegistryManager(path);

            assertEquals(true, manager.isAdministrator("U123"));
            assertEquals(false, manager.isAdministrator("U999"));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
