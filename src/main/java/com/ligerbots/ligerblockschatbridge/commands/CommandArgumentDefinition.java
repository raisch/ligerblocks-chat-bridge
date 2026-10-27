package com.ligerbots.ligerblockschatbridge.commands;

import java.util.Objects;
import java.util.List;

public record CommandArgumentDefinition(
        String name,
        String type,
        boolean required,
        List<String> options) {
    public CommandArgumentDefinition(String name, String type, boolean required) {
        this(name, type, required, List.of());
    }

    public CommandArgumentDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("argument name must not be blank");
        }
        type = Objects.requireNonNull(type, "argument type must not be null").trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("argument type must not be blank");
        }
        options = List.copyOf(options);
    }
}
