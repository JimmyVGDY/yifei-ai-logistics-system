package jimmy.logistics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jimmy.logistics.model.StructuredLogQueryDTO;
import jimmy.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class StructuredLogQueryService {

    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter SECOND_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_SCAN_LINES = 20000;

    private final ObjectMapper objectMapper;
    private final Path logFile;

    public StructuredLogQueryService(ObjectMapper objectMapper,
                                     @Value("${logging.file.name:logs/logistics-management.log}") String logFile) {
        this.objectMapper = objectMapper;
        this.logFile = Paths.get(logFile).toAbsolutePath().normalize();
    }

    public PageResult<Map<String, Object>> query(StructuredLogQueryDTO query) {
        int page = Math.max(1, query == null ? 1 : query.getPage());
        int pageSize = Math.max(1, Math.min(query == null ? 20 : query.getPageSize(), 100));
        List<Map<String, Object>> matched = readStructuredLogs().stream()
                .filter(record -> matches(record, query))
                .sorted(Comparator.comparing(record -> String.valueOf(record.getOrDefault("timestamp", "")), Comparator.reverseOrder()))
                .collect(Collectors.toList());
        int offset = (page - 1) * pageSize;
        int end = Math.min(offset + pageSize, matched.size());
        List<Map<String, Object>> records = offset >= matched.size()
                ? new ArrayList<>()
                : matched.subList(offset, end);
        return new PageResult<>(records, page, pageSize, matched.size());
    }

    private List<Map<String, Object>> readStructuredLogs() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Path file : resolveLogFiles()) {
            // 限制单次扫描行数，避免前端查询日志时一次性读取过大的历史文件。
            records.addAll(readFile(file, MAX_SCAN_LINES - records.size()));
            if (records.size() >= MAX_SCAN_LINES) {
                break;
            }
        }
        return records;
    }

    private List<Path> resolveLogFiles() {
        List<Path> files = new ArrayList<>();
        if (Files.isRegularFile(logFile)) {
            files.add(logFile);
        }
        Path parent = logFile.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return files;
        }
        try (Stream<Path> stream = Files.list(parent)) {
            // 当前日志和滚动 JSON 日志一起查询，按修改时间倒序优先返回最近问题现场。
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("logistics-management."))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .forEach(files::add);
        } catch (IOException exception) {
            log.warn("读取结构化日志目录失败，path={}, reason={}", parent, exception.getMessage());
        }
        return files.stream().distinct().collect(Collectors.toList());
    }

    private List<Map<String, Object>> readFile(Path file, int limit) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (limit <= 0) {
            return records;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && records.size() < limit) {
                parseLine(line, file).ifPresent(records::add);
            }
        } catch (IOException exception) {
            log.warn("读取结构化日志文件失败，path={}, reason={}", file, exception.getMessage());
        }
        return records;
    }

    private java.util.Optional<Map<String, Object>> parseLine(String line, Path file) {
        if (!StringUtils.hasText(line)) {
            return java.util.Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(line);
            Map<String, Object> record = new LinkedHashMap<>();
            // 只抽取前端检索和详情展示需要的字段，缺失字段统一转为空字符串便于页面兜底展示。
            put(record, "timestamp", text(root, "timestamp"));
            put(record, "level", text(root, "level"));
            put(record, "logger", text(root, "logger"));
            put(record, "thread", text(root, "thread"));
            put(record, "message", text(root, "message"));
            put(record, "traceId", text(root, "traceId"));
            put(record, "userId", text(root, "userId"));
            put(record, "userCode", text(root, "userCode"));
            put(record, "usernameMasked", text(root, "usernameMasked"));
            put(record, "roleCode", text(root, "roleCode"));
            put(record, "module", text(root, "module"));
            put(record, "operation", text(root, "operation"));
            put(record, "costMs", text(root, "costMs"));
            put(record, "result", text(root, "result"));
            put(record, "stackTrace", firstText(root, "stack_trace", "stackTrace"));
            put(record, "sourceFile", file.getFileName().toString());
            return java.util.Optional.of(record);
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    private boolean matches(Map<String, Object> record, StructuredLogQueryDTO query) {
        if (query == null) {
            return true;
        }
        return contains(record, "level", query.getLevel())
                && contains(record, "logger", query.getLogger())
                && contains(record, "traceId", query.getTraceId())
                && contains(record, "userId", query.getUserId())
                && contains(record, "userCode", query.getUserCode())
                && contains(record, "usernameMasked", query.getUsernameMasked())
                && contains(record, "roleCode", query.getRoleCode())
                && contains(record, "module", query.getModule())
                && contains(record, "operation", query.getOperation())
                && contains(record, "result", query.getResult())
                && matchesKeyword(record, query.getKeyword())
                && matchesTime(record, query.getStartTime(), query.getEndTime());
    }

    private boolean matchesKeyword(Map<String, Object> record, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lowerKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return record.values().stream()
                .filter(value -> value != null)
                .map(value -> String.valueOf(value).toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(lowerKeyword));
    }

    private boolean matchesTime(Map<String, Object> record, String startTime, String endTime) {
        LocalDateTime timestamp = parseTime(String.valueOf(record.getOrDefault("timestamp", "")));
        if (timestamp == null) {
            // 无法解析时间的历史日志不直接丢弃，避免排查问题时误漏非标准格式日志。
            return true;
        }
        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        return (start == null || !timestamp.isBefore(start)) && (end == null || !timestamp.isAfter(end));
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed, LOG_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            try {
                // 兼容前端 datetime-local 的 T 分隔格式，以及只有秒级精度的日志时间。
                String normalized = trimmed.replace('T', ' ');
                if (normalized.length() >= 23) {
                    return LocalDateTime.parse(normalized.substring(0, 23), LOG_TIME_FORMATTER);
                }
                if (normalized.length() >= 19) {
                    return LocalDateTime.parse(normalized.substring(0, 19), SECOND_TIME_FORMATTER);
                }
                return null;
            } catch (Exception exception) {
                return null;
            }
        }
    }

    private boolean contains(Map<String, Object> record, String field, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        Object value = record.get(field);
        return value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    private void put(Map<String, Object> record, String key, String value) {
        record.put(key, value == null ? "" : value);
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? "" : node.asText();
    }

    private String firstText(JsonNode root, String first, String second) {
        String value = text(root, first);
        return StringUtils.hasText(value) ? value : text(root, second);
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0;
        }
    }
}
