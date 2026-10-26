package com.ligerbots.ligerblockschatbridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class SecretProvider {
    static final String TOKEN_ENV = "LIGERBLOCKS_SLACK_BOT_TOKEN";
    static final String TOKEN_FILE_ENV = "LIGERBLOCKS_SLACK_BOT_TOKEN_FILE";
    static final String APP_TOKEN_ENV = "LIGERBLOCKS_SLACK_APP_TOKEN";
    static final String APP_TOKEN_FILE_ENV = "LIGERBLOCKS_SLACK_APP_TOKEN_FILE";

    private SecretProvider() {
    }

    static String readBotToken() throws IOException {
        String token = System.getenv(TOKEN_ENV);
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        String tokenFile = System.getenv(TOKEN_FILE_ENV);
        if (tokenFile != null && !tokenFile.isBlank()) {
            String fileToken = Files.readString(Path.of(tokenFile), StandardCharsets.UTF_8).trim();
            if (!fileToken.isBlank()) {
                return fileToken;
            }
            throw new IOException("Slack bot token file is empty: " + tokenFile);
        }

        throw new IOException("Missing Slack bot token. Set " + TOKEN_ENV
                + " or " + TOKEN_FILE_ENV + ".");
    }

    static boolean isValidBotToken(String token) {
        return token != null && token.startsWith("xoxb-") && token.length() > 5;
    }

    static String readAppToken() throws IOException {
        return readSecret(APP_TOKEN_ENV, APP_TOKEN_FILE_ENV, "Slack app-level token");
    }

    static boolean isValidAppToken(String token) {
        return token != null && token.startsWith("xapp-") && token.length() > 5;
    }

    private static String readSecret(String valueEnvironment, String fileEnvironment, String name)
            throws IOException {
        String value = System.getenv(valueEnvironment);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        String file = System.getenv(fileEnvironment);
        if (file != null && !file.isBlank()) {
            String fileValue = Files.readString(Path.of(file), StandardCharsets.UTF_8).trim();
            if (!fileValue.isBlank()) {
                return fileValue;
            }
            throw new IOException(name + " file is empty: " + file);
        }

        throw new IOException("Missing " + name + ". Set " + valueEnvironment
                + " or " + fileEnvironment + ".");
    }
}
