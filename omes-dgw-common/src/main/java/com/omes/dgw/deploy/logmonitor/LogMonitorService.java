package com.omes.dgw.deploy.logmonitor;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.deploy.logmonitor.dto.LogContentVo;
import com.omes.dgw.deploy.logmonitor.dto.LogFileVo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 仅尾读有限行（类似 tail -N），不做全文件扫描。 */
@Service
public class LogMonitorService {

    private static final int DEFAULT_TAIL = 200;
    private static final int MAX_TAIL = 500;
    /** log4j2 pattern: ... ] LEVEL logger - msg */
    private static final Pattern LEVEL_TOKEN = Pattern.compile("\\]\\s+(ERROR|WARN|INFO|DEBUG|TRACE)\\s+");
    private static final Set<String> LEVEL_ORDER = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    public List<LogFileVo> listFiles() {
        Path dir = logDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isAllowedName(p.getFileName().toString()))
                    .map(this::toVo)
                    .sorted(Comparator.comparing(LogFileVo::getModifiedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (IOException e) {
            throw new BusinessException("读取日志目录失败: " + e.getMessage());
        }
    }

    public LogContentVo readContent(String name, Integer tailLines, String level, String keyword) {
        Path file = resolveAllowed(name);
        if (!Files.isRegularFile(file)) {
            throw new BusinessException("日志文件不存在: " + name);
        }

        int limit = clamp(tailLines == null ? DEFAULT_TAIL : tailLines, 1, MAX_TAIL);
        String minLevel = normalizeLevel(level);
        String kw = keyword == null ? "" : keyword.trim();
        boolean filtered = !"ALL".equals(minLevel) || !kw.isEmpty();
        // 有过滤时多读一点再筛，仍封顶，避免全查
        int scan = filtered ? Math.min(limit * 5, MAX_TAIL * 5) : limit;

        try {
            long size = Files.size(file);
            Instant modifiedAt = Files.getLastModifiedTime(file).toInstant();
            List<String> raw = readLastLines(file, scan);
            List<String> lines = filterLines(raw, minLevel, kw);
            boolean truncated = lines.size() > limit || (filtered && raw.size() >= scan);
            if (lines.size() > limit) {
                lines = lines.subList(lines.size() - limit, lines.size());
            }
            return LogContentVo.builder()
                    .name(name)
                    .size(size)
                    .modifiedAt(modifiedAt)
                    .lines(lines)
                    .nextOffset(size)
                    .truncated(truncated)
                    .build();
        } catch (IOException e) {
            throw new BusinessException("读取日志失败: " + e.getMessage());
        }
    }

    private List<String> filterLines(List<String> raw, String minLevel, String keyword) {
        List<String> out = new ArrayList<>(Math.min(raw.size(), 256));
        boolean keepStack = "ALL".equals(minLevel);
        for (String line : raw) {
            if (!keyword.isEmpty() && !line.contains(keyword)) {
                if (extractLevel(line) != null) {
                    keepStack = false;
                }
                continue;
            }
            if (!"ALL".equals(minLevel)) {
                String lineLevel = extractLevel(line);
                if (lineLevel != null && LEVEL_ORDER.contains(lineLevel)) {
                    keepStack = rank(lineLevel) >= rank(minLevel);
                    if (!keepStack) {
                        continue;
                    }
                } else if (!keepStack || !isStackContinuation(line)) {
                    continue;
                }
            }
            out.add(line);
        }
        return out;
    }

    private static String extractLevel(String line) {
        var m = LEVEL_TOKEN.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    private static boolean isStackContinuation(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        char c = line.charAt(0);
        return Character.isWhitespace(c)
                || line.startsWith("Caused by:")
                || line.startsWith("Suppressed:");
    }

    private static int rank(String level) {
        return switch (level) {
            case "TRACE" -> 0;
            case "DEBUG" -> 1;
            case "INFO" -> 2;
            case "WARN" -> 3;
            case "ERROR" -> 4;
            default -> -1;
        };
    }

    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return "ALL";
        }
        String u = level.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(u) || LEVEL_ORDER.contains(u)) {
            return u;
        }
        throw new BusinessException("不支持的日志级别: " + level);
    }

    static List<String> readLastLines(Path file, int maxLines) throws IOException {
        if (maxLines <= 0) {
            return List.of();
        }
        long length = Files.size(file);
        if (length == 0) {
            return List.of();
        }
        long chunk = Math.min(length, Math.min((long) maxLines * 512L, 1024L * 1024));
        long start = length - chunk;
        byte[] buf = new byte[(int) chunk];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(start);
            raf.readFully(buf);
        }
        String text = new String(buf, StandardCharsets.UTF_8);
        if (start > 0) {
            int nl = text.indexOf('\n');
            if (nl >= 0) {
                text = text.substring(nl + 1);
            }
        }
        if (text.isEmpty()) {
            return List.of();
        }
        String[] parts = text.split("\\R", -1);
        int end = parts.length;
        if (end > 0 && parts[end - 1].isEmpty()) {
            end--;
        }
        int from = Math.max(0, end - maxLines);
        List<String> lines = new ArrayList<>(end - from);
        for (int i = from; i < end; i++) {
            lines.add(parts[i]);
        }
        return lines;
    }

    private LogFileVo toVo(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return LogFileVo.builder()
                    .name(path.getFileName().toString())
                    .size(attrs.size())
                    .modifiedAt(attrs.lastModifiedTime().toInstant())
                    .build();
        } catch (IOException e) {
            return LogFileVo.builder().name(path.getFileName().toString()).build();
        }
    }

    Path resolveAllowed(String name) {
        if (name == null || name.isBlank() || !isAllowedName(name)) {
            throw new BusinessException("非法日志文件名");
        }
        Path base = logDir();
        Path resolved = base.resolve(name).normalize();
        if (!resolved.startsWith(base)) {
            throw new BusinessException("非法日志路径");
        }
        return resolved;
    }

    static boolean isAllowedName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.endsWith(".gz")) {
            return false;
        }
        return "system-restart.log".equals(name)
                || (name.startsWith("omes-dgw-server") && name.endsWith(".log"));
    }

    static Path logDir() {
        return Path.of(System.getProperty("LOG_PATH", "logs")).toAbsolutePath().normalize();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
