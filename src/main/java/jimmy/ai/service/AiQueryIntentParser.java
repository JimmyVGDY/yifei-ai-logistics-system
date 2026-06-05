package jimmy.ai.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 查询意图解析器：
 * 只识别白名单业务模块和查询条件，不生成 SQL。
 */
@Component
public class AiQueryIntentParser {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(20\\d{2}-\\d{1,2}-\\d{1,2})");

    private static final Pattern BUSINESS_NO_PATTERN =
            Pattern.compile("\\b((?:LO|WB|ORD)-[A-Z0-9-]+|20\\d{15,20})\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PLATE_NO_PATTERN =
            Pattern.compile("[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][A-Z0-9挂学警港澳]{5,6}");

    private static final Pattern STATUS_PATTERN =
            Pattern.compile("(?:状态[是为=:：\\s]*)?(待处理|处理中|已处理|已关闭|待调度|运输中|已完成|已取消|异常|待支付|已支付|未收款|已收款|待接单|已接单)");

    private static final Pattern TECH_ID_PATTERN =
            Pattern.compile("\\b[a-zA-Z0-9][a-zA-Z0-9_-]{8,80}\\b");

    private static final Pattern EXPLICIT_KEYWORD_PATTERN =
            Pattern.compile("(loginSessionId|operationId|traceId|客户名称|客户名|关键词|订单号|运单号|司机名|车牌号|客户|司机|车辆|状态)[是为=:：\\s]*([^，,。；;\\n\\r]+)");

    private static final Pattern QUOTED_KEYWORD_PATTERN =
            Pattern.compile("[“\"']([^”\"']{2,60})[”\"']");

    private static final Pattern ACCIDENTAL_SYMBOL_BOUNDARY_PATTERN =
            Pattern.compile("^[\\p{Punct}\\s，,。；;：:！？?、【】\\[\\]（）()《》<>“”‘’]+|[\\p{Punct}\\s，,。；;：:！？?、【】\\[\\]（）()《》<>“”‘’]+$");

    private static final List<String> WRITE_INTENT_WORDS = List.of(
            "新增", "新建", "创建", "修改", "编辑", "删除",
            "确认收款", "标记收款",
            "导入数据", "导入订单", "导入客户", "导入运单",
            "导出", "导出数据", "导出订单", "导出客户", "导出Excel", "导出excel",
            "上传文件", "上传附件", "上传图片",
            "drop", "delete", "update", "insert", "truncate"
    );

    private static final List<String> DASHBOARD_WORDS = List.of(
            "看板", "统计", "总览", "运营情况", "今日订单", "收入趋势", "订单趋势", "运单趋势"
    );

    private static final List<String> QUERY_STOP_WORDS = List.of(
            "帮我", "帮忙", "麻烦", "查询", "查一下", "查下", "查看", "看看", "看一下", "看下",
            "我要", "我想", "请问", "给我", "显示", "列出", "筛选",
            "今天", "今日", "昨天", "最近7天", "近7天", "最近30天", "近30天",
            "订单", "订单号", "运单", "运单号", "客户", "客户名称", "客户名",
            "司机", "司机名", "车辆", "车牌", "车牌号", "状态",
            "信息", "资料", "情况", "记录", "数据", "列表", "明细", "相关", "只要",
            "的", "是", "为", "等于", "关于"
    );

    private static final List<String> TECH_ID_CONTEXT_WORDS = List.of(
            "traceid", "operationid", "loginsessionid", "trace id", "operation id", "session id",
            "链路", "会话", "操作id", "操作ID", "编号", "id", "ID"
    );

    private final List<ModuleRule> moduleRules = List.of(
            new ModuleRule("operationLogs", "操作日志", "system:log:query",
                    List.of("操作日志", "审计日志", "登录日志", "traceId", "operationId", "loginSessionId")),

            new ModuleRule("waybills", "运单中心", "waybill:query",
                    List.of("运单中心", "运单号", "运单编号", "WB-")),

            new ModuleRule("orders", "运单管理", "order:query",
                    List.of("订单", "订单号", "运单", "运单管理", "下单", "LO-", "ORD-")),

            new ModuleRule("dispatches", "调度管理", "dispatch:query",
                    List.of("调度", "派车", "派单")),

            new ModuleRule("tasks", "运输任务", "task:query",
                    List.of("任务", "运输任务", "配送任务")),

            new ModuleRule("tracks", "物流轨迹", "track:query",
                    List.of("轨迹", "物流轨迹", "到哪里", "运输到哪", "位置", "定位")),

            new ModuleRule("drivers", "司机管理", "driver:query",
                    List.of("司机", "驾驶员", "司机名")),

            new ModuleRule("vehicles", "车辆管理", "vehicle:query",
                    List.of("车辆", "车牌", "车牌号", "货车")),

            new ModuleRule("customers", "客户管理", "customer:query",
                    List.of("客户", "客户名称", "客户名", "货主")),

            new ModuleRule("exceptions", "异常管理", "exception:query",
                    List.of("异常", "报障", "故障", "投诉", "延误", "破损")),

            new ModuleRule("fees", "费用结算", "fee:query",
                    List.of("费用", "结算", "收款", "未收款", "应收", "应付")),

            new ModuleRule("users", "用户管理", "system:user:query",
                    List.of("用户", "账号", "账户")),

            new ModuleRule("roles", "角色管理", "system:role:query",
                    List.of("角色", "权限角色")),

            new ModuleRule("files", "上传文件", "file:query",
                    List.of("文件", "上传记录", "附件", "导入记录"))
    );

    public AiQueryIntent parse(String message) {
        if (!StringUtils.hasText(message)) {
            return AiQueryIntent.unmatched();
        }

        String text = normalizeInput(message);
        if (!StringUtils.hasText(text)) {
            return AiQueryIntent.unmatched();
        }
        String lower = text.toLowerCase(Locale.ROOT);

        if (containsWriteIntent(lower)) {
            return AiQueryIntent.forbiddenWriteIntent();
        }

        TimeRange timeRange = parseTimeRange(text);
        String keyword = parseKeyword(text);

        if (containsAny(text, DASHBOARD_WORDS)) {
            return new AiQueryIntent(
                    "dashboard",
                    "运营看板",
                    "dashboard:view",
                    keyword,
                    timeRange.startTime(),
                    timeRange.endTime(),
                    true,
                    false,
                    true
            );
        }

        for (ModuleRule rule : moduleRules) {
            if (rule.matches(text, lower)) {
                return new AiQueryIntent(
                        rule.module(),
                        rule.moduleName(),
                        rule.permission(),
                        keyword,
                        timeRange.startTime(),
                        timeRange.endTime(),
                        false,
                        false,
                        true
                );
            }
        }

        return AiQueryIntent.unmatched();
    }

    public AiQueryIntent parse(String message, String previousUserMessage) {
        AiQueryIntent current = parse(message);
        if (current.matched() || !StringUtils.hasText(previousUserMessage)) {
            return current;
        }

        AiQueryIntent previous = parse(previousUserMessage);
        if (!previous.matched() || previous.forbiddenWrite()) {
            return current;
        }

        // 追问通常只包含筛选条件，例如“只要待处理的”，这里继承上一轮模块，只用当前句补关键词和时间范围。
        TimeRange currentRange = parseTimeRange(normalizeInput(message));
        String keyword = parseKeyword(normalizeInput(message));
        return new AiQueryIntent(
                previous.module(),
                previous.moduleName(),
                previous.permission(),
                StringUtils.hasText(keyword) ? keyword : previous.keyword(),
                StringUtils.hasText(currentRange.startTime()) ? currentRange.startTime() : previous.startTime(),
                StringUtils.hasText(currentRange.endTime()) ? currentRange.endTime() : previous.endTime(),
                previous.dashboard(),
                false,
                true
        );
    }

    private boolean containsWriteIntent(String lowerText) {
        for (String word : WRITE_INTENT_WORDS) {
            if (lowerText.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String parseKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher businessNoMatcher = BUSINESS_NO_PATTERN.matcher(text);
        if (businessNoMatcher.find()) {
            return cleanupKeyword(businessNoMatcher.group(1));
        }

        Matcher plateNoMatcher = PLATE_NO_PATTERN.matcher(text);
        if (plateNoMatcher.find()) {
            return cleanupKeyword(plateNoMatcher.group());
        }

        Matcher statusMatcher = STATUS_PATTERN.matcher(text);
        if (statusMatcher.find()) {
            return cleanupKeyword(statusMatcher.group(1));
        }

        Matcher explicitMatcher = EXPLICIT_KEYWORD_PATTERN.matcher(text);
        if (explicitMatcher.find()) {
            return cleanupKeyword(explicitMatcher.group(2));
        }

        Matcher quotedMatcher = QUOTED_KEYWORD_PATTERN.matcher(text);
        if (quotedMatcher.find()) {
            return cleanupKeyword(quotedMatcher.group(1));
        }

        if (containsAny(text.toLowerCase(Locale.ROOT), TECH_ID_CONTEXT_WORDS)) {
            Matcher techIdMatcher = TECH_ID_PATTERN.matcher(text);
            if (techIdMatcher.find()) {
                return cleanupKeyword(techIdMatcher.group());
            }
        }

        return fallbackKeyword(text);
    }

    private String normalizeInput(String message) {
        // 用户在聊天框里经常会多打空格、换行、全角符号或零宽字符，先归一化再做意图匹配。
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFKC);
        return normalized
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String fallbackKeyword(String text) {
        String keyword = text;

        for (String stopWord : QUERY_STOP_WORDS) {
            keyword = keyword.replace(stopWord, " ");
        }

        keyword = DATE_PATTERN.matcher(keyword).replaceAll(" ");
        keyword = keyword.replaceAll("[，,。；;：:！？?、\\s]+", " ").trim();

        String[] parts = keyword.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = cleanupKeyword(parts[i]);
            if (StringUtils.hasText(part)) {
                return part;
            }
        }

        return null;
    }

    private String cleanupKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String keyword = stripAccidentalBoundary(value.trim())
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("[，,。；;：:！？?、]+$", "")
                .replaceAll("(的)?(相关)?(信息|资料|情况|记录|数据|列表|明细)$", "")
                .replaceAll("^(名称|名字|姓名)[是为=:：\\s]*", "")
                .trim();

        // 后缀清理后可能重新露出误触符号，例如“唐若琳!! 的相关信息”，这里再剥一遍边界符号。
        keyword = stripAccidentalBoundary(keyword);
        keyword = keyword.replaceAll("[`'\"\\\\]", "");

        if (keyword.length() < 2) {
            return null;
        }

        if (keyword.length() > 80) {
            keyword = keyword.substring(0, 80);
        }

        return StringUtils.hasText(keyword) ? keyword : null;
    }

    private String stripAccidentalBoundary(String value) {
        return ACCIDENTAL_SYMBOL_BOUNDARY_PATTERN.matcher(value).replaceAll("").trim();
    }

    private TimeRange parseTimeRange(String text) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE_ID);

        if (text.contains("今天") || text.contains("今日")) {
            return dayRange(today);
        }

        if (text.contains("昨天")) {
            return dayRange(today.minusDays(1));
        }

        if (text.contains("最近7天") || text.contains("近7天")) {
            return range(today.minusDays(6), today);
        }

        if (text.contains("最近30天") || text.contains("近30天")) {
            return range(today.minusDays(29), today);
        }

        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return new TimeRange(null, null);
        }

        LocalDate start = parseDateSafely(matcher.group(1));
        if (start == null) {
            return new TimeRange(null, null);
        }

        LocalDate end = start;
        if (matcher.find()) {
            LocalDate secondDate = parseDateSafely(matcher.group(1));
            if (secondDate != null) {
                end = secondDate;
            }
        }

        if (end.isBefore(start)) {
            LocalDate temp = start;
            start = end;
            end = temp;
        }

        return range(start, end);
    }

    private TimeRange dayRange(LocalDate date) {
        return range(date, date);
    }

    private TimeRange range(LocalDate start, LocalDate end) {
        return new TimeRange(
                start.atStartOfDay().format(DATE_TIME_FORMATTER),
                end.atTime(23, 59, 59).format(DATE_TIME_FORMATTER)
        );
    }

    private LocalDate parseDateSafely(String value) {
        try {
            String[] parts = value.split("-");
            if (parts.length != 3) {
                return null;
            }

            String dateText = String.format("%04d-%02d-%02d",
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));

            return LocalDate.parse(dateText, DATE_FORMATTER);
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private record TimeRange(String startTime, String endTime) {
    }

    private record ModuleRule(String module, String moduleName, String permission, List<String> keywords) {

        private boolean matches(String text, String lowerText) {
            for (String keyword : keywords) {
                if (text.contains(keyword) || lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }
}
