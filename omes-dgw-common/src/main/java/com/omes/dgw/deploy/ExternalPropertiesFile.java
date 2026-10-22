package com.omes.dgw.deploy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 就地更新 properties 文件中的键值，尽量保留原有注释与顺序。
 */
public final class ExternalPropertiesFile {

    private static final Pattern KEY_LINE = Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*=(.*)$");

    private ExternalPropertiesFile() {
    }

    public static Map<String, String> read(Path file) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return map;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            Matcher m = KEY_LINE.matcher(line);
            if (m.matches()) {
                map.put(m.group(1), unescape(m.group(2).trim()));
            }
        }
        return map;
    }

    public static void upsert(Path file, Map<String, String> updates) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> lines = Files.isRegularFile(file)
                ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                : new ArrayList<>();

        Map<String, String> pending = new LinkedHashMap<>(updates);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            Matcher m = KEY_LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String key = m.group(1);
            if (!pending.containsKey(key)) {
                continue;
            }
            String value = pending.remove(key);
            if (value == null) {
                continue;
            }
            lines.set(i, key + "=" + escape(value));
        }

        if (!pending.isEmpty()) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.add("# --- updated by OMES DGW admin UI ---");
            for (Map.Entry<String, String> e : pending.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                lines.add(e.getKey() + "=" + escape(e.getValue()));
            }
        }

        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".config-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String unescape(String raw) {
        return raw.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
