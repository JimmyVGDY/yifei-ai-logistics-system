package jimmy.ai.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 查询意图解析器：只识别白名单业务模块和查询条件，不生成 SQL。
 */
@Component
public class AiQueryIntentParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}-\\d{1,2}-\\d{1,2})");
    private static final Pattern EXPLICIT_KEYWORD = Pattern.compile("(关键词|客户|客户名|客户名称|订单号|运单号|司机|司机名|车辆|车牌号|状态|traceId|operationId|loginSessionId)[是为:：\\s]*([^，,。；;\\s]+)");
    private static final Pattern ORDER_NO = Pattern.compile("\\b(?:LO|WB)-[A-Z0-9-]+\\b", Pattern.CASE_INSENSITIVE);

    private final List<ModuleRule> moduleRules = List.of(
            new ModuleRule("orders", "运单管理", "order:query", "运单", "订单", "下单"),
            new ModuleRule("customers", "客户管理", "customer:query", "客户"),
            new ModuleRule("waybills", "运单中心", "waybill:query", "运单中心", "运单号"),
            new ModuleRule("dispatches", "调度管理", "dispatch:query", "调度", "派车"),
            new ModuleRule("tasks", "运输任务", "task:query", "任务", "运输任务"),
            new ModuleRule("tracks", "物流轨迹", "track:query", "轨迹", "物流轨迹", "到哪里"),
            new ModuleRule("drivers", "司机管理", "driver:query", "司机"),
            new ModuleRule("vehicles", "车辆管理", "vehicle:query", "车辆", "车牌"),
            new ModuleRule("exceptions", "异常管理", "exception:query", "异常", "报障"),
            new ModuleRule("fees", "费用结算", "fee:query", "费用", "结算", "收款", "未收款"),
            new ModuleRule("users", "用户管理", "system:user:query", "用户", "账号"),
            new ModuleRule("roles", "角色管理", "system:role:query", "角色"),
            new ModuleRule("operationLogs", "操作日志", "system:log:query", "操作日志", "审计日志"),
            new ModuleRule("files", "上传文件", "file:query", "文件", "上传记录")
    );

    public AiQueryIntent parse(String message) {
        if (!StringUtils.hasText(message)) {
            return AiQueryIntent.unmatched();
        }
        String text = message.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsWriteIntent(lower)) {
            return AiQueryIntent.forbiddenWriteIntent();
        }
        TimeRange timeRange = timeRange(text);
        if (containsAny(text, "看板", "统计", "总览", "运营情况", "今日订单", "收入趋势")) {
            return new AiQueryIntent("dashboard", "运营看板", "dashboard:view", keyword(text),
                    timeRange.startTime(), timeRange.endTime(), true, false, true);
        }
        for (ModuleRule rule : moduleRules) {
            if (rule.matches(text)) {
                return new AiQueryIntent(rule.module(), rule.moduleName(), rule.permission(), keyword(text),
                        timeRange.startTime(), timeRange.endTime(), false, false, true);
            }
        }
        return AiQueryIntent.unmatched();
    }

    private boolean containsWriteIntent(String lower) {
        return containsAny(lower, "新增", "新建", "创建", "修改", "编辑", "删除", "确认收款", "标记收款",
                "导入", "导出", "上传", "drop", "delete", "update", "insert", "truncate");
    }

    private String keyword(String text) {
        Matcher orderNo = ORDER_NO.matcher(text);
        if (orderNo.find()) {
            return orderNo.group();
        }
        Matcher explicit = EXPLICIT_KEYWORD.matcher(text);
        if (explicit.find()) {
            return cleanupKeyword(explicit.group(2));
        }
        Matcher quoted = Pattern.compile("[“\"']([^”\"']{2,40})[”\"']").matcher(text);
        if (quoted.find()) {
            return cleanupKeyword(quoted.group(1));
        }
        return null;
    }

    private String cleanupKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String keyword = value.trim().replaceAll("[，,。；;：:]+$", "");
        return StringUtils.hasText(keyword) ? keyword : null;
    }

    private TimeRange timeRange(String text) {
        LocalDate today = LocalDate.now();
        if (text.contains("今天") || text.contains("今日")) {
            return dayRange(today);
        }
        if (text.contains("昨天")) {
            return dayRange(today.minusDays(1));
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return new TimeRange(null, null);
        }
        LocalDate start = parseDate(matcher.group(1));
        LocalDate end = start;
        if (matcher.find()) {
            end = parseDate(matcher.group(1));
        }
        return new TimeRange(start.atStartOfDay().format(DATE_TIME_FORMATTER),
                end.atTime(23, 59, 59).format(DATE_TIME_FORMATTER));
    }

    private TimeRange dayRange(LocalDate date) {
        return new TimeRange(date.atStartOfDay().format(DATE_TIME_FORMATTER),
                date.atTime(23, 59, 59).format(DATE_TIME_FORMATTER));
    }

    private LocalDate parseDate(String value) {
        String[] parts = value.split("-");
        return LocalDate.parse(String.format("%04d-%02d-%02d",
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])), DATE_FORMATTER);
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private record TimeRange(String startTime, String endTime) {
    }

    private record ModuleRule(String module, String moduleName, String permission, String... keywords) {
        private boolean matches(String text) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }
}
