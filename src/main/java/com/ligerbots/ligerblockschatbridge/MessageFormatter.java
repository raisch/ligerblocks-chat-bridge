package com.ligerbots.ligerblockschatbridge;

final class MessageFormatter {
    private MessageFormatter() {
    }

    static String format(String template, String playerName, String message) {
        return template
                .replace("{player}", playerName)
                .replace("{message}", message);
    }
}
