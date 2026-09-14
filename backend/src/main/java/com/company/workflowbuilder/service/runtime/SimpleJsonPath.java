package com.company.workflowbuilder.service.runtime;

import java.util.*;

public final class SimpleJsonPath {
    private SimpleJsonPath() {}

    public static Optional<Object> read(Object root, String path) {
        if (path == null || !path.startsWith("$")) throw new IllegalArgumentException("JSONPath must start with $");
        if ("$".equals(path)) return Optional.ofNullable(root);
        Object current = root;
        int index = 1;
        while (index < path.length()) {
            if (path.charAt(index) == '.') {
                int start = ++index;
                while (index < path.length() && path.charAt(index) != '.' && path.charAt(index) != '[') index++;
                if (start == index || !(current instanceof Map<?, ?> map)) return Optional.empty();
                String key = path.substring(start, index);
                if (!map.containsKey(key)) return Optional.empty();
                current = map.get(key);
            } else if (path.charAt(index) == '[') {
                int end = path.indexOf(']', index);
                if (end < 0 || !(current instanceof List<?> list)) return Optional.empty();
                try {
                    int arrayIndex = Integer.parseInt(path.substring(index + 1, end));
                    if (arrayIndex < 0) arrayIndex = list.size() + arrayIndex;
                    if (arrayIndex < 0 || arrayIndex >= list.size()) return Optional.empty();
                    current = list.get(arrayIndex); index = end + 1;
                } catch (NumberFormatException ex) { return Optional.empty(); }
            } else return Optional.empty();
        }
        return Optional.ofNullable(current);
    }
}
