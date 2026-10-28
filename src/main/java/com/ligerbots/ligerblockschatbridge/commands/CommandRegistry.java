package com.ligerbots.ligerblockschatbridge.commands;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CommandRegistry {
    private final Map<String, CommandMapping> mappings;
    private final java.util.Set<String> admins;

    CommandRegistry(Map<String, CommandMapping> mappings, java.util.Set<String> admins) {
        this.mappings = Collections.unmodifiableMap(new LinkedHashMap<>(mappings));
        this.admins = java.util.Set.copyOf(admins);
    }

    public Optional<CommandMapping> find(String nameOrAlias) {
        if (nameOrAlias == null || nameOrAlias.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mappings.get(nameOrAlias.toLowerCase(Locale.ROOT)));
    }

    public Resolution resolve(String nameOrAlias) {
        if (nameOrAlias == null || nameOrAlias.isBlank()) {
            return Resolution.none();
        }
        String normalized = nameOrAlias.toLowerCase(Locale.ROOT);
        CommandMapping exact = mappings.get(normalized);
        if (exact != null) {
            return Resolution.match(exact);
        }
        Set<CommandMapping> matches = new LinkedHashSet<>();
        mappings.forEach((name, mapping) -> {
            if (name.startsWith(normalized)) {
                matches.add(mapping);
            }
        });
        if (matches.size() == 1) {
            return Resolution.match(matches.iterator().next());
        }
        return matches.isEmpty()
                ? Resolution.none()
                : Resolution.ambiguous(matches.stream().map(CommandMapping::name).sorted().toList());
    }

    public Map<String, CommandMapping> mappings() {
        return mappings;
    }

    public java.util.Set<String> admins() {
        return admins;
    }

    public record Resolution(CommandMapping mapping, List<String> ambiguousNames) {
        public Resolution {
            ambiguousNames = List.copyOf(ambiguousNames);
        }

        static Resolution match(CommandMapping mapping) {
            return new Resolution(mapping, List.of());
        }

        static Resolution ambiguous(List<String> names) {
            return new Resolution(null, names);
        }

        static Resolution none() {
            return new Resolution(null, List.of());
        }

        public boolean isAmbiguous() {
            return !ambiguousNames.isEmpty();
        }

        public boolean isMatch() {
            return mapping != null;
        }
    }
}
