package jimmy.ai.service;

import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * AI 查询参数归一化器。
 * <p>
 * 统一处理模型工具入参和规则兜底查询中的模块、关键词和相对时间，避免某个模块单独打补丁。
 */
public class AiQueryNormalizer {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<ModuleAlias> MODULE_ALIASES = List.of(
            new ModuleAlias("operationLogs", List.of("操作日志", "审计日志", "登录日志", "traceid", "operationid", "loginsessionid")),
            new ModuleAlias("waybills", List.of("运单中心", "运输单", "运输单号", "运单号", "运单编号", "waybills", "waybill", "wb-")),
            new ModuleAlias("orders", List.of("订单管理", "运单管理", "订单", "订单号", "运单", "下单", "orders", "order", "lo-", "ord-")),
            new ModuleAlias("dispatches", List.of("调度管理", "调度", "派车", "派单", "dispatches", "dispatch")),
            new ModuleAlias("tasks", List.of("运输任务", "配送任务", "任务管理", "任务", "tasks", "task")),
            new ModuleAlias("tracks", List.of("物流轨迹", "轨迹", "到哪里", "运输到哪", "位置", "定位", "tracks", "track")),
            new ModuleAlias("drivers", List.of("司机管理", "司机", "驾驶员", "司机名", "drivers", "driver")),
            new ModuleAlias("vehicles", List.of("车辆管理", "车辆", "车牌号", "车牌", "货车", "vehicles", "vehicle")),
            new ModuleAlias("customers", List.of("客户管理", "客户名称", "客户名", "客户", "货主", "customers", "customer")),
            new ModuleAlias("exceptions", List.of("异常管理", "异常记录", "异常", "报障", "故障", "投诉", "延误", "破损", "exceptions", "anomalies")),
            new ModuleAlias("fees", List.of("费用结算", "费用", "结算", "收款", "未收款", "应收", "应付", "fees", "fee")),
            new ModuleAlias("users", List.of("用户管理", "用户", "账号", "账户", "users", "user")),
            new ModuleAlias("roles", List.of("角色管理", "角色", "权限角色", "roles", "role")),
            new ModuleAlias("files", List.of("上传文件", "上传记录", "附件", "文件", "files", "file"))
    );

    private static final List<String> STATUS_WORDS = List.of(
            "待处理", "处理中", "已处理", "已关闭", "待调度", "运输中", "已完成", "已取消",
            "待支付", "已支付", "未收款", "已收款", "待接单", "已接单", "已付款", "未付款"
    );

    private static final List<String> RELATIVE_TIME_WORDS = List.of(
            "今天", "今日", "昨天", "最近7天", "近7天", "最近30天", "近30天",
            "这个月", "本月", "上个月", "上月"
    );

    private static final List<String> QUERY_NOISE_WORDS = List.of(
            "帮我", "帮忙", "麻烦", "查询", "查一下", "查下", "查看", "看看", "看一下", "看下",
            "我要", "我想", "请问", "给我", "显示", "列出", "筛选", "所有的", "全部的",
            "所有", "全部", "全量", "任意", "不限", "这个", "那个", "某个",
            "信息", "资料", "情况", "记录", "数据", "列表", "明细", "相关", "只要", "管理", "中心",
            "的", "是", "为", "等于", "关于", "和", "及", "以及", "与", "跟", "或者"
    );

    public NormalizedQuery normalize(String module, String keyword, String startTime, String endTime, String originalQuestion) {
        String combined = normalizeText(String.join(" ",
                nullToBlank(originalQuestion), nullToBlank(module), nullToBlank(keyword)));
        String normalizedModule = normalizeModule(module);
        if (!StringUtils.hasText(normalizedModule)) {
            normalizedModule = normalizeModule(combined);
        }

        TimeRange relativeRange = parseRelativeTimeRange(combined);
        String safeStart = StringUtils.hasText(relativeRange.startTime())
                ? relativeRange.startTime()
                : blankToNull(startTime);
        String safeEnd = StringUtils.hasText(relativeRange.endTime())
                ? relativeRange.endTime()
                : blankToNull(endTime);

        String safeKeyword = cleanupKeyword(keyword, normalizedModule);
        if (!StringUtils.hasText(safeKeyword)) {
            safeKeyword = extractStatusKeyword(combined, normalizedModule);
        }
        if (!StringUtils.hasText(safeKeyword)) {
            safeKeyword = cleanupKeyword(combined, normalizedModule);
        }

        if (StringUtils.hasText(normalizedModule)
                && !StringUtils.hasText(safeKeyword)
                && !StringUtils.hasText(safeStart)) {
            TimeRange defaultRange = range(LocalDate.now(BUSINESS_ZONE_ID).minusDays(29), LocalDate.now(BUSINESS_ZONE_ID));
            safeStart = defaultRange.startTime();
            safeEnd = defaultRange.endTime();
        }

        return new NormalizedQuery(normalizedModule, safeKeyword, safeStart, safeEnd);
    }

    public String normalizeModule(String value) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (ModuleAlias alias : MODULE_ALIASES) {
            if (alias.module().equals(lower)) {
                return alias.module();
            }
            for (String word : alias.words()) {
                String normalizedWord = normalizeText(word);
                if (text.contains(normalizedWord) || lower.contains(normalizedWord.toLowerCase(Locale.ROOT))) {
                    return alias.module();
                }
            }
        }
        return null;
    }

    private String cleanupKeyword(String value, String module) {
        String keyword = normalizeText(value);
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        for (String word : RELATIVE_TIME_WORDS) {
            keyword = keyword.replace(word, " ");
        }
        for (ModuleAlias alias : MODULE_ALIASES) {
            if (alias.module().equals(module)) {
                for (String word : alias.words().stream()
                        .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                        .toList()) {
                    keyword = keyword.replace(word, " ");
                }
            }
        }
        for (String word : QUERY_NOISE_WORDS.stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList()) {
            keyword = keyword.replace(word, " ");
        }
        keyword = keyword.replaceAll("[，,。；;：:！？?、\\s]+", " ")
                .replaceAll("[`'\"\\\\]", "")
                .trim();
        if (!StringUtils.hasText(keyword) || isWeakKeyword(keyword)) {
            return null;
        }
        String[] parts = keyword.split("\\s+");
        for (String part : parts) {
            String cleaned = part.trim();
            if (StringUtils.hasText(cleaned) && cleaned.length() >= 2 && !isWeakKeyword(cleaned)) {
                return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
            }
        }
        return null;
    }

    private String extractStatusKeyword(String text, String module) {
        if (!StringUtils.hasText(module) || !StringUtils.hasText(text)) {
            return null;
        }
        for (String status : STATUS_WORDS) {
            if (text.contains(status)) {
                return status;
            }
        }
        return null;
    }

    private TimeRange parseRelativeTimeRange(String text) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE_ID);
        if (!StringUtils.hasText(text)) {
            return TimeRange.empty();
        }
        if (text.contains("今天") || text.contains("今日")) {
            return range(today, today);
        }
        if (text.contains("昨天")) {
            return range(today.minusDays(1), today.minusDays(1));
        }
        if (text.contains("最近7天") || text.contains("近7天")) {
            return range(today.minusDays(6), today);
        }
        if (text.contains("最近30天") || text.contains("近30天")) {
            return range(today.minusDays(29), today);
        }
        if (text.contains("这个月") || text.contains("本月")) {
            return range(today.withDayOfMonth(1), today);
        }
        if (text.contains("上个月") || text.contains("上月")) {
            LocalDate lastMonth = today.minusMonths(1);
            return range(lastMonth.withDayOfMonth(1), lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()));
        }
        return TimeRange.empty();
    }

    private TimeRange range(LocalDate start, LocalDate end) {
        return new TimeRange(
                start.atStartOfDay().format(DATE_TIME_FORMATTER),
                end.atTime(23, 59, 59).format(DATE_TIME_FORMATTER)
        );
    }

    private boolean isWeakKeyword(String keyword) {
        return STATUS_WORDS.contains(keyword) || RELATIVE_TIME_WORDS.contains(keyword);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record ModuleAlias(String module, List<String> words) {
    }

    private record TimeRange(String startTime, String endTime) {
        private static TimeRange empty() {
            return new TimeRange(null, null);
        }
    }

    public record NormalizedQuery(String module, String keyword, String startTime, String endTime) {
    }
}
