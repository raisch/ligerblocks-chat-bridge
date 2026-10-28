package com.ligerbots.ligerblockschatbridge.commands;

final class JsonAdminsEditor {
    private JsonAdminsEditor() {
    }

    static String add(String json, String userId) throws CommandConfigurationException {
        String quotedId = "\"" + userId + "\"";
        int[] array = findAdminsArray(json);
        if (array != null) {
            String contents = json.substring(array[0] + 1, array[1]);
            String separator = contents.trim().isEmpty() ? "" : ",";
            return json.substring(0, array[1]) + separator + " " + quotedId + json.substring(array[1]);
        }

        int root = json.indexOf('{');
        if (root < 0) {
            throw new CommandConfigurationException("command configuration must be a JSON object");
        }
        String remainder = json.substring(root + 1);
        boolean hasProperties = !JsonCommentStripper.strip(remainder)
                .replaceAll("\\s", "")
                .isEmpty();
        return json.substring(0, root + 1)
                + "\n  \"admins\": [" + quotedId + "]" + (hasProperties ? "," : "")
                + json.substring(root + 1);
    }

    static String remove(String json, String userId) throws CommandConfigurationException {
        int[] array = findAdminsArray(json);
        if (array == null) {
            throw new CommandConfigurationException("command configuration has no admins array");
        }
        String quotedId = "\"" + userId + "\"";
        int valueStart = json.indexOf(quotedId, array[0] + 1);
        if (valueStart < 0 || valueStart >= array[1]) {
            return json;
        }
        int valueEnd = valueStart + quotedId.length();
        int next = skipWhitespace(json, valueEnd);
        if (next < array[1] && json.charAt(next) == ',') {
            return json.substring(0, valueStart) + json.substring(next + 1);
        }
        int previous = valueStart - 1;
        while (previous > array[0] && Character.isWhitespace(json.charAt(previous))) {
            previous--;
        }
        if (previous > array[0] && json.charAt(previous) == ',') {
            return json.substring(0, previous) + json.substring(valueEnd);
        }
        return json.substring(0, valueStart) + json.substring(valueEnd);
    }

    static String withoutAdmins(String json) throws CommandConfigurationException {
        int[] array = findAdminsArray(json);
        if (array == null) {
            return json;
        }
        int keyStart = json.lastIndexOf("\"admins\"", array[0]);
        int next = skipWhitespace(json, array[1] + 1);
        if (next < json.length() && json.charAt(next) == ',') {
            return json.substring(0, keyStart) + json.substring(next + 1);
        }
        int previous = keyStart - 1;
        while (previous >= 0 && Character.isWhitespace(json.charAt(previous))) {
            previous--;
        }
        if (previous >= 0 && json.charAt(previous) == ',') {
            return json.substring(0, previous) + json.substring(array[1] + 1);
        }
        return json.substring(0, keyStart) + json.substring(array[1] + 1);
    }

    private static int[] findAdminsArray(String json) {
        int depth = 0;
        for (int index = 0; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '"') {
                int end = stringEnd(json, index);
                String value = json.substring(index + 1, end);
                if (depth == 1 && "admins".equals(value)) {
                    int next = skipWhitespace(json, end + 1);
                    if (next < json.length() && json.charAt(next) == ':') {
                        next = skipWhitespace(json, next + 1);
                        if (next < json.length() && json.charAt(next) == '[') {
                            return new int[] {next, matchingBracket(json, next, '[', ']')};
                        }
                    }
                }
                index = end;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
            } else if (current == '/' && index + 1 < json.length() && json.charAt(index + 1) == '/') {
                index = lineCommentEnd(json, index + 2);
            } else if (current == '/' && index + 1 < json.length() && json.charAt(index + 1) == '*') {
                index = blockCommentEnd(json, index + 2);
            }
        }
        return null;
    }

    private static int matchingBracket(String json, int start, char opening, char closing) {
        int depth = 0;
        for (int index = start; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '"') {
                index = stringEnd(json, index);
            } else if (current == opening) {
                depth++;
            } else if (current == closing && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int stringEnd(String json, int start) {
        boolean escaped = false;
        for (int index = start + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (!escaped && current == '"') {
                return index;
            }
            escaped = !escaped && current == '\\';
            if (current != '\\') {
                escaped = false;
            }
        }
        return json.length() - 1;
    }

    private static int skipWhitespace(String json, int index) {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int lineCommentEnd(String json, int index) {
        while (index < json.length() && json.charAt(index) != '\n' && json.charAt(index) != '\r') {
            index++;
        }
        return index - 1;
    }

    private static int blockCommentEnd(String json, int index) {
        int end = json.indexOf("*/", index);
        return end < 0 ? json.length() - 1 : end + 1;
    }
}
