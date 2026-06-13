package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiToolCall;
import jimmy.ai.util.SseChatContext;
import jimmy.ai.model.AiGeneratedSqlQueryResult;
import jimmy.ai.model.AiQueryIntent;
import jimmy.ai.model.AiQueryCursor;
import jimmy.ai.model.AiReadonlyQueryResult;
import jimmy.logistics.model.LogisticsDashboardSummary;
import jimmy.logistics.model.ModuleQueryDTO;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.logistics.service.LogisticsRequirementService;
import jimmy.common.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AI 只读查询服务：统一承接 Spring AI 工具调用和规则兜底查询。
 * <p>
 * 这里仍然不让模型直接访问 Mapper/JdbcTemplate。所有查询都复用现有模块白名单、
 * 权限校验、客户数据隔离、分页和脱敏逻辑；复杂统计才走受控临时 SELECT 网关。
 */
@Slf4j
@Service
public class AiReadonlyQueryService {

    private static final String PERMISSION_DENIED_MESSAGE = "当前账号权限不足，无法查询该类数据。如有需要，请联系系统管理员。";
    private static final String CUSTOMER_NOT_BOUND_MESSAGE = "当前账号未绑定客户档案，无法查询相关业务数据，请联系系统管理员。";
    private static final String QUERY_FAILED_MESSAGE = "查询暂时失败，请稍后重试或联系系统管理员。";
    private static final String WRITE_REFUSED_MESSAGE = "当前 AI 助手仅支持只读查询和信息整理，不能执行新增、修改、删除、导入、导出或上传操作。";
    /** AI 默认只返回前 10 条结构化数据；更多数据通过查询游标继续分页查看。 */
    private static final int STRUCTURED_RESULT_LIMIT = 10;

    private static final List<SearchModule> SEARCH_MODULES = List.of(
            new SearchModule("customers", "客户管理", "customer:query", 10),
            new SearchModule("orders", "运单管理", "order:query", 20),
            new SearchModule("waybills", "运单中心", "waybill:query", 30),
            new SearchModule("dispatches", "调度管理", "dispatch:query", 40),
            new SearchModule("tasks", "运输任务", "task:query", 50),
            new SearchModule("tracks", "物流轨迹", "track:query", 60),
            new SearchModule("drivers", "司机管理", "driver:query", 70),
            new SearchModule("vehicles", "车辆管理", "vehicle:query", 80),
            new SearchModule("exceptions", "异常管理", "exception:query", 90),
            new SearchModule("fees", "费用结算", "fee:query", 100),
            new SearchModule("users", "用户管理", "system:user:query", 200),
            new SearchModule("roles", "角色管理", "system:role:query", 210),
            new SearchModule("files", "上传文件", "file:query", 220),
            new SearchModule("operationLogs", "操作日志", "system:log:query", 230)
    );

    private static final Map<String, List<String>> JOINED_SCENES = buildJoinedScenes();

    private static final Map<String, List<String>> STATUS_ALIASES = Map.ofEntries(
            Map.entry("启用", List.of("ACTIVE", "ENABLED", "1")),
            Map.entry("停用", List.of("DISABLED", "INACTIVE", "0")),
            Map.entry("空闲", List.of("AVAILABLE", "IDLE")),
            Map.entry("休息中", List.of("RESTING")),
            Map.entry("运输中", List.of("IN_TRANSIT", "TRANSPORTING", "ON_ROUTE")),
            Map.entry("维修中", List.of("MAINTENANCE")),
            Map.entry("暂停", List.of("PAUSED")),
            Map.entry("已创建", List.of("CREATED")),
            Map.entry("待调度", List.of("WAIT_DISPATCH")),
            Map.entry("已调度", List.of("DISPATCHED")),
            Map.entry("已分配", List.of("ASSIGNED")),
            Map.entry("处理中", List.of("PROCESSING", "HANDLING")),
            Map.entry("已揽收", List.of("PICKED_UP")),
            Map.entry("已到达", List.of("ARRIVED")),
            Map.entry("派送中", List.of("DELIVERING")),
            Map.entry("已送达", List.of("DELIVERED")),
            Map.entry("已签收", List.of("SIGNED")),
            Map.entry("已完成", List.of("COMPLETED", "FINISHED")),
            Map.entry("已取消", List.of("CANCELLED", "CANCELED")),
            Map.entry("异常", List.of("EXCEPTION")),
            Map.entry("待处理", List.of("WAIT_HANDLE")),
            Map.entry("已处理", List.of("CLOSED")),
            Map.entry("已关闭", List.of("CLOSED")),
            Map.entry("已付款", List.of("PAID")),
            Map.entry("已支付", List.of("PAID")),
            Map.entry("未付款", List.of("UNPAID")),
            Map.entry("未收款", List.of("UNPAID")),
            Map.entry("待支付", List.of("UNPAID")),
            Map.entry("部分付款", List.of("PART_PAID")),
            Map.entry("已退款", List.of("REFUNDED")),
            Map.entry("成功", List.of("SUCCESS")),
            Map.entry("失败", List.of("FAILED"))
    );

    private final AiQueryIntentParser intentParser;
    private final AiGeneratedSqlQueryService generatedSqlQueryService;
    private final LogisticsRequirementService logisticsRequirementService;
    private final AiQuerySummaryService summaryService;
    private final AiSensitiveDataMasker masker;
    private final AiQueryCursorService cursorService;
    private final AiToolCallContext toolCallContext;

    public AiReadonlyQueryService(AiQueryIntentParser intentParser,
                                  AiGeneratedSqlQueryService generatedSqlQueryService,
                                  LogisticsRequirementService logisticsRequirementService,
                                  AiQuerySummaryService summaryService,
                                  AiSensitiveDataMasker masker) {
        this(intentParser, generatedSqlQueryService, logisticsRequirementService, summaryService, masker, null,
                new AiToolCallContext(8));
    }

    public AiReadonlyQueryService(AiQueryIntentParser intentParser,
                                  AiGeneratedSqlQueryService generatedSqlQueryService,
                                  LogisticsRequirementService logisticsRequirementService,
                                  AiQuerySummaryService summaryService,
                                  AiSensitiveDataMasker masker,
                                  AiQueryCursorService cursorService,
                                  AiToolCallContext toolCallContext) {
        this.intentParser = intentParser;
        this.generatedSqlQueryService = generatedSqlQueryService;
        this.logisticsRequirementService = logisticsRequirementService;
        this.summaryService = summaryService;
        this.masker = masker;
        this.cursorService = cursorService;
        this.toolCallContext = toolCallContext;
    }

    public AiReadonlyQueryResult query(String message) {
        return query(message, null);
    }

    public AiReadonlyQueryResult query(String message, String previousUserMessage) {
        return query(message, previousUserMessage, toolCallContext.conversationId(), toolCallContext.userId(), toolCallContext.userCode());
    }

    public AiReadonlyQueryResult query(String message, String previousUserMessage, String conversationId, String userId, String userCode) {
        String normalizedMessage = normalizeForSearch(message);
        AiQueryIntent quickIntent = nullToUnmatched(hasText(previousUserMessage)
                ? intentParser.parse(normalizedMessage, previousUserMessage)
                : intentParser.parse(normalizedMessage));
        if (quickIntent.forbiddenWrite()) {
            return simpleResult("只读安全校验", "只读模式", WRITE_REFUSED_MESSAGE);
        }

        /*
         * “查看剩余、继续看、下一批”这类追问没有新的业务对象，本质是沿用上一轮查询。
         * 不在这里兜底的话，模型可能把“剩余28条”当成全新关键词，进而丢失上一轮模块和时间条件。
         */
        if (isContinuationRequest(normalizedMessage) && hasText(previousUserMessage)) {
            Optional<AiReadonlyQueryResult> cursorResult = queryByCursor(conversationId, userId, userCode);
            if (cursorResult.isPresent()) {
                return cursorResult.get();
            }
            return query(previousUserMessage, null, conversationId, userId, userCode);
        }

        AiGeneratedSqlQueryResult sqlQueryResult = generatedSqlQueryService.query(normalizedMessage);
        if (sqlQueryResult.executed()) {
            return new AiReadonlyQueryResult(true, sqlQueryResult.message(),
                    List.of(citation("临时只读 SQL 查询", sqlQueryResult.message())),
                    List.of(new AiToolCall("临时只读 SQL 查询", "关联查询", sqlQueryResult.message())));
        }

        if (quickIntent.dashboard()) {
            return queryDashboard(quickIntent);
        }

        if (isGlobalSearchRequest(normalizedMessage) && hasText(previousUserMessage)) {
            String previousKeyword = resolveKeyword(previousUserMessage, null);
            if (hasText(previousKeyword)) {
                return globalSearch(previousKeyword, null, null, null, conversationId, userId, userCode);
            }
        }

        if (shouldUseJoinedQuery(normalizedMessage)) {
            AiReadonlyQueryResult result = joinedSearch(resolveJoinedScene(normalizedMessage), resolveKeyword(normalizedMessage, previousUserMessage),
                    quickIntent.startTime(), quickIntent.endTime(), conversationId, userId, userCode);
            if (result.executed()) {
                return result;
            }
        }

        if (shouldUseGlobalSearch(normalizedMessage, quickIntent)) {
            String keyword = resolveKeyword(normalizedMessage, previousUserMessage);
            return globalSearch(keyword, quickIntent.startTime(), quickIntent.endTime(), null, conversationId, userId, userCode);
        }

        AiReadonlyQueryResult result = queryByIntent(quickIntent, conversationId, userId, userCode);
        if (shouldFallbackToGlobalSearch(quickIntent, result)) {
            return globalSearch(quickIntent.keyword(), quickIntent.startTime(), quickIntent.endTime(), quickIntent.module(),
                    conversationId, userId, userCode);
        }
        return result;
    }

    /**
     * Spring AI Tool Calling 的单模块查询入口。
     * 模型传入的是模块中文名或模块编码，后端统一解析成白名单模块后再执行。
     */
    public AiReadonlyQueryResult queryModule(String moduleText, String keyword, String startTime, String endTime) {
        SearchModule module = findModule(moduleText);
        if (module == null) {
            return simpleResult("业务数据查询", "业务模块", "未识别要查询的业务模块，请补充订单、客户、车辆、司机、异常、费用等查询范围。");
        }
        return queryModule(module, cleanupKeyword(keyword), startTime, endTime, 1, STRUCTURED_RESULT_LIMIT, "业务数据查询",
                toolCallContext.conversationId(), toolCallContext.userId(), toolCallContext.userCode());
    }

    public AiReadonlyQueryResult globalSearch(String keyword, String startTime, String endTime) {
        return globalSearch(cleanupKeyword(keyword), startTime, endTime, null,
                toolCallContext.conversationId(), toolCallContext.userId(), toolCallContext.userCode());
    }

    public AiReadonlyQueryResult joinedSearch(String sceneText, String keyword, String startTime, String endTime) {
        return joinedSearch(sceneText, keyword, startTime, endTime,
                toolCallContext.conversationId(), toolCallContext.userId(), toolCallContext.userCode());
    }

    private AiReadonlyQueryResult joinedSearch(String sceneText, String keyword, String startTime, String endTime,
                                               String conversationId, String userId, String userCode) {
        String keywordToUse = cleanupKeyword(keyword);
        List<SearchModule> modules = resolveJoinedModules(sceneText);
        if (modules.isEmpty()) {
            return AiReadonlyQueryResult.empty();
        }

        List<AiCitation> citations = new ArrayList<>();
        List<AiToolCall> toolCalls = new ArrayList<>();
        StringBuilder answer = new StringBuilder("已执行业务联合查询：")
                .append(joinModuleNames(modules))
                .append(hasText(keywordToUse) ? "，关键词“" + keywordToUse + "”。" : "，默认最近数据。");
        long total = 0;
        int queriedModules = 0;
        List<Map<String, Object>> allRows = new ArrayList<>();
        List<String> allColumns = null;

        for (SearchModule module : modules) {
            AiReadonlyQueryResult result = queryModule(module, keywordToUse, startTime, endTime, 1, 20, "业务联合查询",
                    conversationId, userId, userCode);
            if (!result.executed()) {
                continue;
            }
            queriedModules++;
            total += extractHitCount(result.toolCalls());
            citations.addAll(result.citations());
            toolCalls.addAll(result.toolCalls());
            answer.append("\n\n").append(result.answerContext());
            if (result.rows() != null && !result.rows().isEmpty()) {
                allRows.addAll(result.rows());
                if (allColumns == null && result.columns() != null) {
                    allColumns = result.columns();
                }
            }
        }

        if (queriedModules == 0) {
            return simpleResult("业务联合查询", "业务链路", PERMISSION_DENIED_MESSAGE);
        }
        if (total == 0) {
            String message = "已在当前账号可访问的关联业务模块中查询，暂未匹配到记录。已查询范围：" + joinModuleNames(modules) + "。";
            return simpleResult("业务联合查询", joinModuleNames(modules), message);
        }
        return new AiReadonlyQueryResult(true, masker.mask(answer.toString()), citations, toolCalls,
                allRows, allColumns == null ? List.of() : allColumns);
    }

    public AiReadonlyQueryResult queryDashboard() {
        AiQueryIntent intent = new AiQueryIntent("dashboard", "运营看板", "dashboard:view",
                null, null, null, true, false, true);
        return queryDashboard(intent);
    }

    private AiReadonlyQueryResult queryByIntent(AiQueryIntent intent, String conversationId, String userId, String userCode) {
        if (!intent.matched()) {
            return AiReadonlyQueryResult.empty();
        }
        if (intent.forbiddenWrite()) {
            return simpleResult("只读安全校验", "只读模式", WRITE_REFUSED_MESSAGE);
        }
        if (intent.dashboard()) {
            return queryDashboard(intent);
        }
        SearchModule module = findModule(intent.module());
        if (module == null) {
            return AiReadonlyQueryResult.empty();
        }
        return queryModule(module, intent.keyword(), intent.startTime(), intent.endTime(), 1, STRUCTURED_RESULT_LIMIT, "业务数据查询",
                conversationId, userId, userCode);
    }

    private Optional<AiReadonlyQueryResult> queryByCursor(String conversationId, String userId, String userCode) {
        if (cursorService == null) {
            return Optional.empty();
        }
        Optional<AiQueryCursor> optionalCursor = cursorService.latest(conversationId, userId, userCode);
        if (optionalCursor.isEmpty()) {
            return Optional.empty();
        }
        AiQueryCursor cursor = optionalCursor.get();
        SearchModule module = findModule(cursor.getModuleCode());
        if (module == null) {
            return Optional.empty();
        }
        int nextPage = cursor.getPage() == null ? 2 : cursor.getPage() + 1;
        int pageSize = cursor.getPageSize() == null ? STRUCTURED_RESULT_LIMIT : cursor.getPageSize();
        return Optional.of(queryModule(module, cursor.getKeyword(), cursor.getStartTime(), cursor.getEndTime(),
                nextPage, pageSize, cursor.getToolName() == null ? "业务数据查询" : cursor.getToolName(),
                conversationId, userId, userCode));
    }

    private AiReadonlyQueryResult queryDashboard(AiQueryIntent intent) {
        if (!hasPermission(intent.permission())) {
            return simpleResult("运营看板查询", intent.moduleName(), PERMISSION_DENIED_MESSAGE);
        }
        LogisticsDashboardSummary summary = logisticsRequirementService.dashboardSummary();
        String answerContext = masker.mask(summaryService.dashboardSummary(summary));
        return new AiReadonlyQueryResult(true, answerContext,
                List.of(citation(intent.moduleName(), answerContext)),
                List.of(new AiToolCall("运营看板查询", intent.moduleName(), answerContext)));
    }

    private AiReadonlyQueryResult queryModule(SearchModule module, String keyword, String startTime, String endTime, int pageSize, String toolName) {
        return queryModule(module, keyword, startTime, endTime, 1, pageSize, toolName,
                toolCallContext.conversationId(), toolCallContext.userId(), toolCallContext.userCode());
    }

    private AiReadonlyQueryResult queryModule(SearchModule module, String keyword, String startTime, String endTime,
                                             int pageNo, int pageSize, String toolName,
                                             String conversationId, String userId, String userCode) {
        if (!hasPermission(module.permission())) {
            log.info("AI 只读查询被权限拦截，module={}, permission={}", module.module(), module.permission());
            return simpleResult(toolName, module.moduleName(), PERMISSION_DENIED_MESSAGE);
        }

        try {
            List<String> keywordCandidates = expandKeywordCandidates(keyword);
            List<ModuleRecordVO> mergedRecords = new ArrayList<>();
            long total = 0;
            PageResult<ModuleRecordVO> firstPage = null;

            for (String candidate : keywordCandidates) {
                ModuleQueryDTO query = new ModuleQueryDTO();
                query.setPage(pageNo);
                query.setPageSize(pageSize);
                query.setKeyword(candidate);
                query.setStartTime(startTime);
                query.setEndTime(endTime);
                PageResult<ModuleRecordVO> page = logisticsRequirementService.modulePage(module.module(), query);
                if (firstPage == null) {
                    firstPage = page;
                }
                total += page.total();
                addDistinctRecords(mergedRecords, page.records(), pageSize);
                if (!hasText(keyword) || !mergedRecords.isEmpty()) {
                    break;
                }
            }

            PageResult<ModuleRecordVO> page = new PageResult<>(
                    mergedRecords,
                    firstPage == null ? 1 : firstPage.page(),
                    firstPage == null ? pageSize : firstPage.pageSize(),
                    total
            );
            AiQueryIntent intent = new AiQueryIntent(module.module(), module.moduleName(), module.permission(),
                    keyword, startTime, endTime, false, false, true);
            String summary = masker.mask(summaryService.moduleSummary(intent, page));
            String result = masker.mask(summaryService.queryConditionSummary(intent) + "，命中 " + page.total() + " 条记录。");
            // 提取结构化数据行，过滤内部字段并映射为中文列名
            List<Map<String, Object>> rows = buildDisplayRows(mergedRecords);
            List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
            int returnedCount = Math.max(0, (pageNo - 1) * pageSize + rows.size());
            long remainingCount = Math.max(0, page.total() - returnedCount);
            Optional<AiQueryCursor> cursor = cursorService == null ? Optional.empty() : cursorService.create(
                    conversationId,
                    userId,
                    userCode,
                    "MODULE",
                    toolName,
                    module.module(),
                    module.moduleName(),
                    keyword,
                    startTime,
                    endTime,
                    null,
                    pageNo,
                    pageSize,
                    page.total(),
                    returnedCount,
                    result
            );
            String cursorId = cursor.map(AiQueryCursor::getCursorId).orElse(null);
            String nextPageHint = remainingCount > 0 ? "还有 " + remainingCount + " 条记录，可输入“继续看”或“查看剩余数据”。" : null;
            return new AiReadonlyQueryResult(true, summary,
                    List.of(citation(module.moduleName(), summary)),
                    List.of(new AiToolCall(toolName, module.moduleName(), result)),
                    rows, columns, cursorId, page.total(), returnedCount, remainingCount, remainingCount > 0, nextPageHint);
        } catch (IllegalStateException exception) {
            log.info("AI 只读查询被数据范围拦截，module={}, reason={}", module.module(), exception.getMessage());
            return simpleResult(toolName, module.moduleName(), CUSTOMER_NOT_BOUND_MESSAGE);
        } catch (RuntimeException exception) {
            log.warn("AI 只读查询失败，module={}, reason={}", module.module(), exception.getMessage(), exception);
            return simpleResult(toolName, module.moduleName(), QUERY_FAILED_MESSAGE);
        }
    }

    /**
     * 全场景模糊查找：只查当前账号有权限的模块，并按业务相关度排序输出。
     */
    private AiReadonlyQueryResult globalSearch(String keyword, String startTime, String endTime, String excludedModule,
                                               String conversationId, String userId, String userCode) {
        String keywordToUse = cleanupKeyword(keyword);
        if (!hasText(keywordToUse)) {
            return simpleResult("全场景模糊搜索", "业务模块", "请补充客户名、订单号、运单号、司机名、车牌号、地址、状态或时间范围等查询条件。");
        }

        List<AiCitation> citations = new ArrayList<>();
        List<AiToolCall> toolCalls = new ArrayList<>();
        List<Map<String, Object>> allRows = new ArrayList<>();
        List<String> allColumns = null;
        StringBuilder answer = new StringBuilder("已按关键词“").append(keywordToUse).append("”进行全场景模糊搜索。");
        long total = 0;
        int queriedModules = 0;

        for (SearchModule module : SEARCH_MODULES.stream().sorted(Comparator.comparingInt(SearchModule::priority)).toList()) {
            if (module.module().equals(excludedModule)) {
                continue;
            }
            if (!hasPermission(module.permission())) {
                continue;
            }
            AiReadonlyQueryResult result = queryModule(module, keywordToUse, startTime, endTime, 1, 20, "全场景模糊搜索",
                    conversationId, userId, userCode);
            if (!result.executed()) {
                continue;
            }
            queriedModules++;
            long hitCount = extractHitCount(result.toolCalls());
            total += hitCount;
            if (hitCount > 0) {
                citations.addAll(result.citations());
                toolCalls.addAll(result.toolCalls());
                answer.append("\n\n").append(result.answerContext());
                if (result.rows() != null && !result.rows().isEmpty()) {
                    allRows.addAll(result.rows());
                    if (allColumns == null && result.columns() != null) {
                        allColumns = result.columns();
                    }
                }
            }
        }

        if (queriedModules == 0) {
            return simpleResult("全场景模糊搜索", "业务模块", PERMISSION_DENIED_MESSAGE);
        }
        if (total == 0) {
            String message = "已在当前账号可访问的业务模块中按关键词“" + keywordToUse + "”查找，暂未匹配到记录。"
                    + "可补充订单号、运单号、客户名称、手机号、车牌号、司机姓名、地址或时间范围继续查询。";
            return simpleResult("全场景模糊搜索", "业务模块", message);
        }
        return new AiReadonlyQueryResult(true, masker.mask(answer.toString()), citations, toolCalls,
                allRows, allColumns == null ? List.of() : allColumns);
    }

    private boolean shouldFallbackToGlobalSearch(AiQueryIntent intent, AiReadonlyQueryResult result) {
        return intent.matched()
                && hasText(intent.keyword())
                && result.answerContext().contains("共匹配 0 条记录")
                && List.of("customers", "orders", "waybills", "drivers", "vehicles").contains(intent.module());
    }

    private boolean shouldUseGlobalSearch(String message, AiQueryIntent intent) {
        if (isGlobalSearchRequest(message)) {
            return true;
        }
        String keyword = cleanupKeyword(resolveKeyword(message, null));
        return hasText(keyword)
                && keyword.equals(normalizeForSearch(message))
                && !isFollowUpFilter(message)
                && (!intent.matched() || "customers".equals(intent.module()));
    }

    private boolean shouldUseJoinedQuery(String message) {
        String text = normalizeForSearch(message);
        return containsAny(text, List.of("全貌", "完整链路", "相关的所有", "所有物流信息", "订单和费用", "运单和订单",
                "任务和轨迹", "调度和任务", "异常影响", "对应费用", "及客户", "以及客户", "和客户"));
    }

    private String resolveJoinedScene(String message) {
        String text = normalizeForSearch(message);
        if (containsAny(text, List.of("司机", "驾驶员"))) {
            return "driver";
        }
        if (containsAny(text, List.of("车辆", "车牌", "沪", "粤", "京", "浙", "苏"))) {
            return "vehicle";
        }
        if (containsAny(text, List.of("客户", "客户全貌", "相关的所有物流信息", "订单和费用", "及客户", "以及客户"))) {
            return "customer";
        }
        if (containsAny(text, List.of("异常影响", "异常订单", "待处理异常"))) {
            return "exception";
        }
        if (containsAny(text, List.of("订单", "运单管理", "完整链路", "运单和订单"))) {
            return "order";
        }
        return "business";
    }

    private List<SearchModule> resolveJoinedModules(String sceneText) {
        String scene = normalizeForSearch(sceneText).toLowerCase(Locale.ROOT);
        List<String> moduleCodes = JOINED_SCENES.getOrDefault(scene, JOINED_SCENES.get("business"));
        return moduleCodes.stream()
                .map(this::findModule)
                .filter(module -> module != null && hasPermission(module.permission()))
                .toList();
    }

    private String resolveKeyword(String message, String previousUserMessage) {
        AiQueryIntent current = nullToUnmatched(intentParser.parse(message));
        if (hasText(current.keyword())) {
            return normalizeResolvedKeyword(current.keyword());
        }
        if (isModuleClarification(message) && hasText(previousUserMessage)) {
            AiQueryIntent previous = nullToUnmatched(intentParser.parse(previousUserMessage));
            if (hasText(previous.keyword())) {
                return normalizeResolvedKeyword(previous.keyword());
            }
            String fallbackPrevious = fallbackStandaloneKeyword(previousUserMessage);
            if (hasText(fallbackPrevious)) {
                return fallbackPrevious;
            }
        }
        String standalone = fallbackStandaloneKeyword(message);
        if (hasText(standalone)) {
            return standalone;
        }
        return null;
    }

    private String fallbackStandaloneKeyword(String message) {
        String text = normalizeForSearch(message);
        if (!hasText(text) || isGlobalSearchRequest(text) || isBroadQuestion(text) || shouldUseJoinedQuery(text)) {
            return null;
        }
        String cleaned = text;
        for (String word : List.of("帮我", "帮忙", "查询", "查一下", "查下", "查看", "看看", "看一下", "看下", "查",
                "我要", "我想", "给我", "显示", "列出", "筛选", "相关", "信息", "资料", "情况", "记录", "数据",
                "列表", "明细", "客户名称", "客户名", "客户", "订单", "运单", "司机", "车辆", "车牌号", "车牌",
                "异常", "费用", "任务", "轨迹", "管理", "中心", "的", "是", "为", "一个")) {
            cleaned = cleaned.replace(word, " ");
        }
        cleaned = cleaned.replaceAll("[，,。；;：:！？?、\\s]+", " ").trim();
        if (!hasText(cleaned)) {
            return null;
        }
        String[] parts = cleaned.split("\\s+");
        return cleanupKeyword(parts[0]);
    }

    private String normalizeResolvedKeyword(String keyword) {
        String cleaned = cleanupKeyword(keyword);
        if (!hasText(cleaned)) {
            return null;
        }
        return cleanupKeyword(cleaned.replaceFirst("^(查|查询|查看|看看|看一下|查一下|查下)", ""));
    }

    private boolean isGlobalSearchRequest(String message) {
        if (!hasText(message)) {
            return false;
        }
        return containsAny(message, List.of("全局查找", "全局搜索", "全局查询", "全场景", "到处找", "所有模块"));
    }

    private boolean isContinuationRequest(String message) {
        if (!hasText(message)) {
            return false;
        }
        return containsAny(message, List.of(
                "剩余", "余下", "剩下", "后面的", "后续", "更多",
                "继续看", "接着看", "下一批", "下一页", "查看更多"
        ));
    }

    private boolean isModuleClarification(String message) {
        String text = normalizeForSearch(message);
        return text.length() <= 16 && containsAny(text, List.of("是客户", "是一个客户", "客户", "是司机", "司机",
                "是车辆", "车辆", "是订单", "订单", "是运单", "运单"));
    }

    private boolean isFollowUpFilter(String message) {
        String text = normalizeForSearch(message);
        return containsAny(text, List.of("只要", "只看", "筛选", "状态", "今天", "昨日", "昨天", "最近7天", "近7天", "最近30天", "近30天"));
    }

    private boolean isBroadQuestion(String message) {
        return containsAny(message, List.of("所有", "全部", "全量", "不限")) && !containsAny(message, List.of("叫", "名称", "名字"));
    }

    private List<String> expandKeywordCandidates(String keyword) {
        String cleaned = cleanupKeyword(keyword);
        if (!hasText(cleaned)) {
            // List.of() 不允许 null 元素，使用空字符串作为单候选（无关键词时等价于不过滤）
            return List.of("");
        }
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(cleaned);
        List<String> statusCodes = STATUS_ALIASES.get(cleaned);
        if (statusCodes != null) {
            candidates.addAll(statusCodes);
        }
        return new ArrayList<>(candidates);
    }

    private void addDistinctRecords(List<ModuleRecordVO> target, List<ModuleRecordVO> source, int limit) {
        Set<Object> existedIds = new LinkedHashSet<>();
        for (ModuleRecordVO record : target) {
            existedIds.add(record.getOrDefault("id", record.toString()));
        }
        for (ModuleRecordVO record : source) {
            Object id = record.getOrDefault("id", record.toString());
            if (existedIds.add(id)) {
                target.add(record);
            }
            if (target.size() >= limit) {
                return;
            }
        }
    }

    private long extractHitCount(List<AiToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return 0;
        }
        String result = toolCalls.getFirst().result();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("命中\\s+(\\d+)\\s+条").matcher(result);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return result.contains("权限不足") || result.contains("未绑定") ? 0 : 1;
    }

    private SearchModule findModule(String moduleText) {
        if (!hasText(moduleText)) {
            return null;
        }
        String text = normalizeForSearch(moduleText);
        String lower = text.toLowerCase(Locale.ROOT);
        for (SearchModule module : SEARCH_MODULES) {
            if (module.module().equals(lower)
                    || module.module().equals(text)
                    || module.moduleName().equals(text)
                    || text.contains(module.moduleName())) {
                return module;
            }
        }
        return null;
    }

    private static Map<String, List<String>> buildJoinedScenes() {
        Map<String, List<String>> scenes = new LinkedHashMap<>();
        scenes.put("customer", List.of("customers", "orders", "waybills", "tracks", "fees", "exceptions"));
        scenes.put("order", List.of("orders", "waybills", "dispatches", "tasks", "tracks", "fees", "exceptions"));
        scenes.put("driver", List.of("drivers", "dispatches", "tasks", "tracks", "exceptions"));
        scenes.put("vehicle", List.of("vehicles", "dispatches", "tasks", "tracks", "exceptions"));
        scenes.put("exception", List.of("exceptions", "orders", "waybills", "tasks", "fees"));
        scenes.put("business", List.of("customers", "orders", "waybills", "dispatches", "tasks", "tracks", "exceptions", "fees"));
        return scenes;
    }

    /**
     * 将搜索模块名称列表拼接为中文顿号分隔的字符串，用于 AI 工具调用返回结果的标题展示。
     */
    private String joinModuleNames(List<SearchModule> modules) {
        return String.join("、", modules.stream().map(SearchModule::moduleName).toList());
    }

    /**
     * 快捷构建单条 AI 只读查询结果，自动脱敏消息并生成工具调用记录。
     */
    /**
     * 将原始数据库记录转换为仅包含面向用户字段的中文显示行。
     * 排除 id、时间戳、审计字段、原始状态码（保留 statusLabel），并映射为中文列名。
     */
    private List<Map<String, Object>> buildDisplayRows(List<ModuleRecordVO> records) {
        // 不展示给用户的内部字段
        Set<String> EXCLUDE_FIELDS = Set.of(
            "id", "created_at", "updated_at", "create_time", "update_time",
            "deleted", "version", "create_by", "update_by",
            "login_session_id", "trace_id", "operation_id"
        );
        // 有 statusLabel 时隐藏原始 status 字段
        Set<String> REDUNDANT_STATUS = Set.of(
            "status", "transport_status", "dispatch_status", "task_status",
            "exception_status", "payment_status", "pay_status"
        );
        // 数据库字段名 → 中文显示名
        Map<String, String> LABELS = new LinkedHashMap<>();
        LABELS.put("customer_name", "客户名称");
        LABELS.put("customer_code", "客户编码");
        LABELS.put("contact_name", "联系人");
        LABELS.put("contact_phone", "联系电话");
        LABELS.put("province", "省份");
        LABELS.put("city", "城市");
        LABELS.put("address", "地址");
        LABELS.put("statusLabel", "状态");
        LABELS.put("order_no", "订单号");
        LABELS.put("waybill_no", "运单号");
        LABELS.put("task_no", "任务号");
        LABELS.put("cargo_name", "货物名称");
        LABELS.put("cargo_weight", "货物重量(kg)");
        LABELS.put("driver_name", "司机");
        LABELS.put("driver_code", "司机编号");
        LABELS.put("vehicle_no", "车牌号");
        LABELS.put("vehicle_type", "车型");
        LABELS.put("real_name", "姓名");
        LABELS.put("username", "用户名");
        LABELS.put("user_code", "用户编号");
        LABELS.put("role_name", "角色");
        LABELS.put("role_code", "角色编码");
        LABELS.put("start_site", "出发地");
        LABELS.put("target_site", "目的地");
        LABELS.put("current_location", "当前位置");
        LABELS.put("sender_address", "发货地址");
        LABELS.put("receiver_address", "收货地址");
        LABELS.put("base_fee", "基础运费");
        LABELS.put("payable_fee", "应付金额");
        LABELS.put("actual_fee", "实付金额");
        LABELS.put("exception_type", "异常类型");
        LABELS.put("exception_desc", "异常描述");
        LABELS.put("report_user", "上报人");
        LABELS.put("report_time", "上报时间");
        LABELS.put("handle_user", "处理人");
        LABELS.put("handle_time", "处理时间");
        LABELS.put("operator_name", "操作人");
        LABELS.put("operation_desc", "操作描述");
        LABELS.put("operation_time", "操作时间");
        LABELS.put("warehouse_name", "仓库");
        LABELS.put("route_code", "路线编号");
        LABELS.put("distance_km", "距离(km)");
        LABELS.put("bill_no", "账单号");
        LABELS.put("base_amount", "基础金额");
        LABELS.put("payable_amount", "应付金额");
        LABELS.put("proof_url", "凭证链接");
        LABELS.put("comment", "备注");

        List<Map<String, Object>> result = new ArrayList<>();
        for (ModuleRecordVO record : records) {
            Map<String, Object> displayRow = new LinkedHashMap<>();
            // 检查是否有 statusLabel 类字段
            boolean hasStatusLabel = record.keySet().stream().anyMatch(k -> k.endsWith("Label"));
            for (String key : record.keySet()) {
                if (EXCLUDE_FIELDS.contains(key)) continue;
                if (hasStatusLabel && REDUNDANT_STATUS.contains(key)) continue;
                String label = LABELS.getOrDefault(key, null);
                if (label != null) {
                    displayRow.put(label, record.get(key));
                } else {
                    // 未映射的字段用原 key（兜底，避免丢数据）
                    displayRow.put(key, record.get(key));
                }
            }
            result.add(displayRow);
        }
        return result;
    }

    private AiReadonlyQueryResult simpleResult(String toolName, String target, String message) {
        String safeMessage = masker.mask(message);
        return new AiReadonlyQueryResult(true, safeMessage,
                List.of(citation(target, safeMessage)),
                List.of(new AiToolCall(toolName, target, safeMessage)));
    }

    /**
     * 安全解包查询意图，null 时回退为"未匹配"意图，避免后续空指针。
     */
    private AiQueryIntent nullToUnmatched(AiQueryIntent intent) {
        return intent == null ? AiQueryIntent.unmatched() : intent;
    }

    /**
     * 权限检查：SSE 异步线程优先读 {@link SseChatContext}，
     * 同步请求仍走 {@link StpUtil}。
     */
    private boolean hasPermission(String permission) {
        String sseLoginId = SseChatContext.getLoginId();
        if (sseLoginId != null) {
            // SSE 异步线程：使用 Controller 预捕获的权限列表
            return SseChatContext.hasPermission(permission);
        }
        // 同步 HTTP 请求：正常走 SaToken
        return StpUtil.hasPermission(permission);
    }

    /**
     * 快捷构建 AI 引用来源对象，自动脱敏摘要内容。
     */
    private AiCitation citation(String target, String snippet) {
        return new AiCitation("business-query", "业务数据查询", target, masker.mask(snippet));
    }

    /**
     * 检查文本是否包含给定词列表中的任意一个词，用于关键词匹配判断。
     */
    private boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 搜索文本标准化：Unicode 正规化（NFKC）+ 去除零宽字符 + 空白合并。
     * <p>
     * 统一用户输入中的全角/半角、变体字符和不可见控制字符，提升模糊搜索命中率。
     */
    private String normalizeForSearch(String value) {
        if (!hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 搜索关键词清洗：去标点、去语气词、限制长度，提取可用于数据库模糊查询的核心词。
     * <p>
     * 处理步骤：标准化 → 去除首尾标点 → 去掉口语化后缀 → 去除引号 → 长度校验（最少 2 字符，最多 80 字符）。
     *
     * @param value 原始用户输入
     * @return 清洗后的关键词，无法提取有效关键词时返回 null
     */
    private String cleanupKeyword(String value) {
        if (!hasText(value)) {
            return null;
        }
        String keyword = normalizeForSearch(value)
                .replaceAll("^[\\p{Punct}\\s，,。；;：:！？?、【】\\[\\]（）()《》<>“”‘’]+", "")
                .replaceAll("[\\p{Punct}\\s，,。；;：:！？?、【】\\[\\]（）()《》<>“”‘’]+$", "")
                .replaceAll("(的)?(相关)?(信息|资料|情况|记录|数据|列表|明细)$", "")
                .replaceAll("[`'\"\\\\]", "")
                .trim();
        if (keyword.length() < 2) {
            return null;
        }
        return keyword.length() > 80 ? keyword.substring(0, 80) : keyword;
    }

    /**
     * 安全判断字符串是否有文本内容，null-safe 的空值检查快捷方法。
     */
    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private record SearchModule(String module, String moduleName, String permission, int priority) {
    }
}
