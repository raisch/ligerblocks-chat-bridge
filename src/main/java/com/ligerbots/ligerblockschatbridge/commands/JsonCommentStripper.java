package com.ligerbots.ligerblockschatbridge.commands;

final class JsonCommentStripper {
    private JsonCommentStripper() {
    }

    static String strip(String json) throws CommandConfigurationException {
        StringBuilder result = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < json.length(); index++) {
            char current = json.charAt(index);
            if (inString) {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
                result.append(current);
            } else if (current == '/' && index + 1 < json.length() && json.charAt(index + 1) == '/') {
                index = skipLineComment(json, index + 2, result);
            } else if (current == '/' && index + 1 < json.length() && json.charAt(index + 1) == '*') {
                index = skipBlockComment(json, index + 2, result);
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static int skipLineComment(String json, int index, StringBuilder result) {
        while (index < json.length()) {
            char current = json.charAt(index);
            if (current == '\n' || current == '\r') {
                result.append(current);
                return index;
            }
            index++;
        }
        return json.length() - 1;
    }

    private static int skipBlockComment(String json, int index, StringBuilder result)
            throws CommandConfigurationException {
        while (index < json.length()) {
            char current = json.charAt(index);
            if (current == '*' && index + 1 < json.length() && json.charAt(index + 1) == '/') {
                return index + 1;
            }
            if (current == '\n' || current == '\r') {
                result.append(current);
            }
            index++;
        }
        throw new CommandConfigurationException("unterminated JSON block comment");
    }
}
