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
import jimmy.ai.model.AiQueryIntent;

/**
 * AI 查询意图解析器：
 * 1. 只识别白名单业务模块和查询条件
 * 2. 不生成 SQL
 * 3. 拦截新增、修改、删除、导入、导出等写操作意图
 */
@Component
public class AiQueryIntentParser {

    /**
     * 业务时区固定为北京时间，避免服务器部署在 UTC 时导致“今天/昨天”判断错误。
     */
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 日期格式：2026-06-05 或 2026-6-5。
     */
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(20\\d{2}-\\d{1,2}-\\d{1,2})");

    /**
     * 业务编号：
     * 1. LO-xxx
     * 2. WB-xxx
     * 3. ORD-xxx
     * 4. 20 开头的长数字编号
     */
    private static final Pattern BUSINESS_NO_PATTERN =
            Pattern.compile("\\b((?:LO|WB|ORD)-[A-Z0-9-]+|20\\d{15,20})\\b", Pattern.CASE_INSENSITIVE);

    /**
     * 中国车牌号，兼容普通车牌和新能源车牌。
     */
    private static final Pattern PLATE_NO_PATTERN =
            Pattern.compile("[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][A-Z0-9挂学警港澳]{5,6}");

    /**
     * 显式状态条件：
     * 只有用户明确说“状态为xxx / 状态是xxx”时，才把它当成状态关键词。
     * 避免“查询异常订单”里的“异常”被误当成 keyword。
     */
    private static final Pattern EXPLICIT_STATUS_PATTERN =
            Pattern.compile("状态[是为=:：\\s]*(待处理|处理中|已处理|已关闭|待调度|运输中|已完成|已取消|异常|待支付|已支付|未收款|已收款|待接单|已接单)");

    /**
     * 追问状态条件：
     * 用于“只要待处理的”“筛选已完成”这种追问。
     * 注意这里暂时不放“异常”，因为“异常”更容易和异常模块意图冲突。
     */
    private static final Pattern FOLLOW_UP_STATUS_PATTERN =
            Pattern.compile("(?:只要|只看|筛选|查|查询|显示|列出)?\\s*(待处理|处理中|已处理|已关闭|待调度|运输中|已完成|已取消|待支付|已支付|未收款|已收款|待接单|已接单)(?:的)?");

    private static final Pattern MODULE_STATUS_PATTERN =
            Pattern.compile("(待处理|处理中|已处理|已关闭|待调度|运输中|已完成|已取消|待支付|已支付|未收款|已收款|待接单|已接单|已付款|未付款)");

    /**
     * 技术 ID，例如 traceId、operationId、loginSessionId。
     * 不能无脑兜底识别，否则客户名、公司名里的英文也可能被误判成 ID。
     */
    private static final Pattern TECH_ID_PATTERN =
            Pattern.compile("\\b[a-zA-Z0-9][a-zA-Z0-9_-]{8,80}\\b");

    /**
     * 显式字段关键词。
     * 例如：
     * 客户名是上海物流
     * 司机名为张三
     * 车牌号：粤B12345
     *
     * 后面的内容不直接贪婪吃到底，会在 cleanupKeyword 里继续清理。
     */
    private static final Pattern EXPLICIT_KEYWORD_PATTERN =
            Pattern.compile("(loginSessionId|operationId|traceId|客户名称|客户名|关键词|订单号|运单号|司机名|车牌号|客户(?!管理)|司机(?!管理)|车辆(?!管理)|状态)[是为=:：\\s]*([^，,。；;\\n\\r]+)");

    /**
     * 引号关键词。
     * 例如：查询“上海测试物流”的订单。
     */
    private static final Pattern QUOTED_KEYWORD_PATTERN =
            Pattern.compile("[“\"']([^”\"']{2,60})[”\"']");

    /**
     * 清理用户误输入在关键词两边的符号。
     */
    private static final Pattern ACCIDENTAL_SYMBOL_BOUNDARY_PATTERN =
            Pattern.compile("^[\\p{Punct}\\s，,。；;：:！？?、【】\\[\\]（）()《》<>“”‘’]+|[\\p{Punct}\\s，,。；;：:！？?、【】\\[\\]（）()《》<>“”‘’]+$");

    /**
     * 明确的中文写操作关键词。
     * 注意：不要单独放“上传”，否则“查询上传记录”会被误判成写操作。
     */
    private static final List<String> WRITE_INTENT_WORDS = List.of(
            "新增", "新建", "创建", "修改", "编辑", "删除",
            "确认收款", "标记收款",
            "导入数据", "导入订单", "导入客户", "导入运单",
            "导出", "导出数据", "导出订单", "导出客户", "导出Excel", "导出excel",
            "上传文件", "上传附件", "上传图片"
    );

    /**
     * 英文 SQL 写操作只拦截明显 SQL 语句形态。
     * 不再单独拦截 update/delete/insert，避免“查询包含 update 的操作日志”被误杀。
     */
    private static final List<Pattern> SQL_WRITE_PATTERNS = List.of(
            Pattern.compile("\\bdrop\\s+table\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdelete\\s+from\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bupdate\\s+\\w+\\s+set\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\binsert\\s+into\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btruncate\\s+table\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final List<String> DASHBOARD_WORDS = List.of(
            "看板", "统计", "总览", "运营情况", "今日订单", "收入趋势", "订单趋势", "运单趋势"
    );

    /**
     * 兜底关键词提取时要移除的查询废话。
     */
    private static final List<String> QUERY_STOP_WORDS = List.of(
            "帮我", "帮忙", "麻烦", "查询", "查一下", "查下", "查看", "看看", "看一下", "看下",
            "我要", "我想", "请问", "给我", "显示", "列出", "筛选",
            "全局查找", "全局搜索", "全局查询", "全局",
            "所有的", "全部的", "所有", "全部", "全量", "任意", "不限",
            "今天", "今日", "昨天", "最近7天", "近7天", "最近30天", "近30天",
            "这个月", "本月", "上个月", "上月",
            "一个", "这个", "那个", "某个",
            "订单", "订单号", "运单", "运单号", "客户", "客户名称", "客户名",
            "司机", "司机名", "车辆", "车牌", "车牌号", "状态", "任务", "运输任务", "配送任务",
            "运单管理", "运单中心", "客户管理", "调度管理", "运输任务", "物流轨迹",
            "司机管理", "车辆管理", "异常管理", "费用结算", "用户管理", "角色管理",
            "操作日志", "审计日志", "登录日志", "上传文件", "上传记录", "附件",
            "信息", "资料", "情况", "记录", "数据", "列表", "明细", "相关", "只要", "管理", "中心",
            "的", "是", "为", "等于", "关于", "和", "及", "以及", "与", "跟", "或者"
    );

    /**
     * 显式关键词提取后，遇到这些词说明后面大概率是筛选条件或模块描述，不应该继续算进 keyword。
     */
    private static final List<String> KEYWORD_CUT_WORDS = List.of(
            "今天", "今日", "昨天", "最近7天", "近7天", "最近30天", "近30天",
            "订单", "运单", "记录", "列表", "明细", "信息", "数据", "资料", "情况",
            "状态", "只要", "筛选", "并且", "然后", "还有"
    );

    /**
     * 只有文本里出现这些上下文时，才启用技术 ID 提取。
     */
    private static final List<String> TECH_ID_CONTEXT_WORDS = List.of(
            "traceid", "operationid", "loginsessionid", "trace id", "operation id", "session id",
            "链路", "会话", "操作id", "操作ID", "编号", "id", "ID"
    );

    /**
     * 缺少模块时，纯中文名称优先作为客户/联系人线索处理。
     * 这类输入常见于用户偷懒只输入“陈土豆”，不能盲目继承上一轮模块。
     */
    private static final Pattern STANDALONE_CHINESE_KEYWORD_PATTERN =
            Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9·（）()\\-]{2,30}$");

    /**
     * 只有这些筛选类追问才允许继承上一轮模块。
     */
    private static final List<String> FOLLOW_UP_FILTER_WORDS = List.of(
            "只要", "只看", "筛选", "状态", "今天", "今日", "昨天", "最近7天", "近7天", "最近30天", "近30天"
    );

    /**
     * 模块规则。
     * 顺序很重要：越精确的规则越靠前。
     */
    private final List<ModuleRule> moduleRules = List.of(
            new ModuleRule("operationLogs", "操作日志", "system:log:query",
                    List.of("操作日志", "审计日志", "登录日志", "traceId", "operationId", "loginSessionId")),

            new ModuleRule("waybills", "运单中心", "waybill:query",
                    List.of("运单中心", "运输单", "运输单号", "运单号", "运单编号", "WB-")),

            new ModuleRule("orders", "运单管理", "order:query",
                    List.of("订单管理", "运单管理", "订单", "订单号", "运单", "下单", "LO-", "ORD-")),

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

        if (containsWriteIntent(text, lower)) {
            return AiQueryIntent.forbiddenWriteIntent();
        }

        TimeRange timeRange = parseTimeRange(text);
        String keyword = parseKeyword(text, false);

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
                if (!StringUtils.hasText(keyword)) {
                    keyword = parseModuleStatusKeyword(text);
                }
                return buildIntent(rule, keyword, timeRange);
            }
        }

        /*
         * 用户只输入一个业务名称时，先按客户线索查询。
         * 如果后续要做真正的全局检索，可以在 AiReadonlyQueryService 中扩展为多模块召回。
         */
        if (isStandaloneBusinessKeyword(text, keyword)) {
            return buildIntent(customerRule(), keyword, timeRange);
        }

        return AiQueryIntent.unmatched();
    }

    /**
     * 支持多轮追问。
     * 例如：
     * 上一轮：查询异常订单
     * 当前轮：只要待处理的
     *
     * 当前轮没有模块时，继承上一轮模块，只替换当前轮提供的 keyword/timeRange。
     */
    public AiQueryIntent parse(String message, String previousUserMessage) {
        AiQueryIntent current = parse(message);

        /*
         * 安全重点：
         * 如果当前句是写操作，必须立即返回 forbidden。
         * 不能因为当前句没有匹配模块，就继承上一轮模块，否则“删除这些”可能绕过写操作拦截。
         */
        if (current.forbiddenWrite() || !StringUtils.hasText(previousUserMessage)) {
            return current;
        }

        AiQueryIntent previous = parse(previousUserMessage);
        if (!previous.matched() || previous.forbiddenWrite()) {
            return current;
        }

        String normalizedCurrent = normalizeInput(message);
        TimeRange currentRange = parseTimeRange(normalizedCurrent);

        if (current.matched()) {
            if (isModuleClarification(normalizedCurrent, current) && StringUtils.hasText(previous.keyword())) {
                return new AiQueryIntent(
                        current.module(),
                        current.moduleName(),
                        current.permission(),
                        previous.keyword(),
                        StringUtils.hasText(currentRange.startTime()) ? currentRange.startTime() : previous.startTime(),
                        StringUtils.hasText(currentRange.endTime()) ? currentRange.endTime() : previous.endTime(),
                        current.dashboard(),
                        false,
                        true
                );
            }
            return current;
        }

        if (!isFollowUpFilterOnly(normalizedCurrent)) {
            return current;
        }

        /*
         * 追问场景允许识别“只要待处理的”这种状态词。
         * 普通单句查询不宽泛识别，避免“异常订单”被误判成 keyword。
         */
        String keyword = parseKeyword(normalizedCurrent, true);

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

    /**
     * 判断是否存在写操作意图。
     */
    private boolean containsWriteIntent(String text, String lowerText) {
        for (String word : WRITE_INTENT_WORDS) {
            if (lowerText.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        for (Pattern pattern : SQL_WRITE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 关键词提取。
     *
     * @param text             归一化后的文本
     * @param followUpAllowed  是否允许按追问方式识别状态词
     */
    private String parseKeyword(String text, boolean followUpAllowed) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        // 1. 业务编号优先
        Matcher businessNoMatcher = BUSINESS_NO_PATTERN.matcher(text);
        if (businessNoMatcher.find()) {
            return cleanupKeyword(businessNoMatcher.group(1));
        }

        // 2. 车牌号优先
        Matcher plateNoMatcher = PLATE_NO_PATTERN.matcher(text);
        if (plateNoMatcher.find()) {
            return cleanupKeyword(plateNoMatcher.group());
        }

        // 3. 显式状态：状态为待处理
        Matcher explicitStatusMatcher = EXPLICIT_STATUS_PATTERN.matcher(text);
        if (explicitStatusMatcher.find()) {
            return cleanupKeyword(explicitStatusMatcher.group(1));
        }

        // 4. 追问状态：只要待处理的
        if (followUpAllowed) {
            Matcher followUpStatusMatcher = FOLLOW_UP_STATUS_PATTERN.matcher(text);
            if (followUpStatusMatcher.matches() || followUpStatusMatcher.find()) {
                return cleanupKeyword(followUpStatusMatcher.group(1));
            }
        }

        // 5. 显式字段：客户名是xxx、司机名为xxx
        Matcher explicitMatcher = EXPLICIT_KEYWORD_PATTERN.matcher(text);
        if (explicitMatcher.find()) {
            return cleanupKeyword(cutKeywordTail(explicitMatcher.group(2)));
        }

        // 6. 引号内容
        Matcher quotedMatcher = QUOTED_KEYWORD_PATTERN.matcher(text);
        if (quotedMatcher.find()) {
            return cleanupKeyword(quotedMatcher.group(1));
        }

        // 7. 技术 ID：必须有上下文词才提取
        if (containsAny(text.toLowerCase(Locale.ROOT), TECH_ID_CONTEXT_WORDS)) {
            Matcher techIdMatcher = TECH_ID_PATTERN.matcher(text);
            if (techIdMatcher.find()) {
                return cleanupKeyword(techIdMatcher.group());
            }
        }

        // 8. 最后兜底
        return fallbackKeyword(text);
    }

    /**
     * 输入归一化：
     * 1. 全角转半角
     * 2. 移除零宽字符
     * 3. 换行、制表符统一为空格
     * 4. 多空格压缩
     */
    private String normalizeInput(String message) {
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFKC);
        return normalized
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 显式关键词提取后，截断后面明显不属于关键词的内容。
     * 例如：
     * 客户是上海物流 今天的订单
     * 提取后先得到：上海物流 今天的订单
     * 截断后得到：上海物流
     */
    private String cutKeywordTail(String rawKeyword) {
        if (!StringUtils.hasText(rawKeyword)) {
            return rawKeyword;
        }

        String result = rawKeyword;
        for (String cutWord : KEYWORD_CUT_WORDS) {
            int index = result.indexOf(cutWord);
            if (index > 0) {
                result = result.substring(0, index);
            }
        }

        return result.trim();
    }

    /**
     * 兜底关键词：
     * 去掉常见查询废话、日期、标点后，从剩余片段中挑选一个相对像业务关键词的词。
     */
    private String fallbackKeyword(String text) {
        String keyword = text;
        if (isBroadModuleQuery(keyword)) {
            return null;
        }

        // 长词优先，避免先替换“任务/客户”导致“运输任务/客户管理”残留成“运输/管理”。
        for (String stopWord : QUERY_STOP_WORDS.stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList()) {
            keyword = keyword.replace(stopWord, " ");
        }

        keyword = DATE_PATTERN.matcher(keyword).replaceAll(" ");
        keyword = keyword.replaceAll("[，,。；;：:！？?、\\s]+", " ").trim();

        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        String[] parts = keyword.split("\\s+");

        /*
         * 优先找非状态、非模块类片段。
         * 避免“查询上海物流客户的异常记录”最后取到“异常”。
         */
        for (String part : parts) {
            String cleaned = cleanupKeyword(part);
            if (StringUtils.hasText(cleaned) && !isWeakKeyword(cleaned)) {
                return cleaned;
            }
        }

        // 如果没有更好的片段，再从后往前取一个可用片段。
        for (int i = parts.length - 1; i >= 0; i--) {
            String cleaned = cleanupKeyword(parts[i]);
            if (StringUtils.hasText(cleaned) && (!isWeakKeyword(cleaned) || isStandaloneStatusKeyword(cleaned))) {
                return cleaned;
            }
        }

        return null;
    }

    /**
     * 宽泛查全量的问法不应该生成 keyword。
     * 例如“所有的运输任务”“全部客户管理数据”“所有的运单和订单”。
     */
    private boolean isBroadModuleQuery(String text) {
        if (!containsAny(text, List.of("所有", "全部", "全量", "不限"))) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean moduleHit = moduleRules.stream().anyMatch(rule -> rule.matches(text, lower));
        if (!moduleHit && !containsAny(text, DASHBOARD_WORDS)) {
            return false;
        }
        String remainder = text;
        for (ModuleRule rule : moduleRules) {
            for (String moduleKeyword : rule.keywords().stream()
                    .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                    .toList()) {
                remainder = remainder.replace(moduleKeyword, " ");
            }
        }
        for (String stopWord : QUERY_STOP_WORDS.stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList()) {
            remainder = remainder.replace(stopWord, " ");
        }
        remainder = remainder.replaceAll("[，,。；;：:！？?、\\s]+", "").trim();
        return !StringUtils.hasText(remainder);
    }

    /**
     * 弱关键词：
     * 这些词本身更像模块名、状态名或筛选词，不适合作为兜底 keyword。
     */
    private boolean isWeakKeyword(String keyword) {
        List<String> weakWords = List.of(
                "所有", "全部", "全量", "任意", "不限", "一个", "这个", "那个", "某个",
                "订单", "运单", "客户", "司机", "车辆", "任务", "运输任务", "配送任务",
                "管理", "中心",
                "异常", "费用", "结算", "收款", "未收款", "已收款",
                "运单管理", "运单中心", "客户管理", "调度管理", "物流轨迹",
                "司机管理", "车辆管理", "异常管理", "费用结算", "用户管理", "角色管理",
                "操作日志", "审计日志", "登录日志", "上传文件", "上传记录", "附件",
                "待处理", "处理中", "已处理", "已关闭",
                "待调度", "运输中", "已完成", "已取消", "待支付", "已支付", "待接单", "已接单"
        );

        return weakWords.contains(keyword);
    }

    private boolean isStandaloneStatusKeyword(String keyword) {
        return List.of(
                "待处理", "处理中", "已处理", "已关闭",
                "待调度", "运输中", "已完成", "已取消",
                "待支付", "已支付", "待接单", "已接单",
                "未收款", "已收款"
        ).contains(keyword);
    }

    private AiQueryIntent buildIntent(ModuleRule rule, String keyword, TimeRange timeRange) {
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

    private ModuleRule customerRule() {
        return moduleRules.stream()
                .filter(rule -> "customers".equals(rule.module()))
                .findFirst()
                .orElseThrow();
    }

    private boolean isStandaloneBusinessKeyword(String text, String keyword) {
        return StringUtils.hasText(keyword)
                && keyword.equals(text)
                && STANDALONE_CHINESE_KEYWORD_PATTERN.matcher(keyword).matches()
                && !isWeakKeyword(keyword);
    }

    /**
     * “是一个客户”“这是客户”属于对上一轮关键词的模块补充，不是新的关键词查询。
     */
    private boolean isModuleClarification(String text, AiQueryIntent current) {
        return current.matched()
                && !StringUtils.hasText(current.keyword())
                && text.length() <= 12
                && containsAny(text, List.of("客户", "订单", "运单", "司机", "车辆", "任务", "异常", "费用"));
    }

    private boolean isFollowUpFilterOnly(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (containsAny(text, FOLLOW_UP_FILTER_WORDS)) {
            return true;
        }
        return FOLLOW_UP_STATUS_PATTERN.matcher(text).matches();
    }

    /**
     * 清理关键词：
     * 1. 去掉首尾误触符号
     * 2. 去掉零宽字符
     * 3. 去掉危险字符
     * 4. 控制长度
     */
    private String cleanupKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String keyword = stripAccidentalBoundary(value.trim())
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("[，,。；;：:！？?、]+$", "")
                .replaceAll("(的)?(相关)?(信息|资料|情况|记录|数据|列表|明细)$", "")
                .replaceAll("(的)?相关$", "")
                .replaceAll("的$", "")
                .replaceAll("^(名称|名字|姓名)[是为=:：\\s]*", "")
                .trim();

        keyword = stripAccidentalBoundary(keyword);

        /*
         * 这里不是替代 SQL 参数化。
         * 后续 Mapper 查询仍然必须使用 MyBatis 参数绑定，不能字符串拼接 SQL。
         */
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

    /**
     * 时间范围解析。
     */
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

        if (text.contains("这个月") || text.contains("本月")) {
            return range(today.withDayOfMonth(1), today);
        }

        if (text.contains("上个月") || text.contains("上月")) {
            LocalDate lastMonth = today.minusMonths(1);
            return range(lastMonth.withDayOfMonth(1), lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()));
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

    /**
     * 安全解析日期，避免 2026-99-99 这种非法日期导致接口异常。
     */
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

    private String parseModuleStatusKeyword(String text) {
        Matcher matcher = MODULE_STATUS_PATTERN.matcher(text);
        if (matcher.find()) {
            return cleanupKeyword(matcher.group(1));
        }
        return null;
    }

    private record TimeRange(String startTime, String endTime) {
    }

    private record ModuleRule(String module, String moduleName, String permission, List<String> keywords) {

        /**
         * 同时支持中文原文匹配和英文大小写不敏感匹配。
         */
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
