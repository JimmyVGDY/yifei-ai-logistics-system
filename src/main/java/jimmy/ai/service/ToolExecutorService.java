package jimmy.ai.service;

import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiGeneratedSqlQueryResult;
import jimmy.ai.model.AiLogAnalysisResponse;
import jimmy.ai.model.AiLogAnalyzeRequest;
import jimmy.ai.model.AiLogTimelineItem;
import jimmy.ai.model.AiReadonlyQueryResult;
import jimmy.ai.model.AiToolCall;
import jimmy.ai.util.SseChatContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工具执行服务 —— 根据工具名称和参数执行对应的只读查询工具。
 * <p>
 * 由 Python AI 服务通过内部端点调用。在执行前设置 SSE 用户上下文，
 * 确保底层服务能正确进行权限校验和数据隔离；执行完毕后清理上下文。
 * <p>
 * 所有返回数据均经过 {@link AiSensitiveDataMasker} 脱敏处理，
 * 每次工具调用都会记录审计日志。
 */
@Slf4j
@Service
public class ToolExecutorService {

    private static final String INTERNAL_CONVERSATION_ID = "AI_INTERNAL";

    private final AiReadonlyQueryService readonlyQueryService;
    private final AiLogAnalysisService logAnalysisService;
    private final AiGeneratedSqlQueryService generatedSqlQueryService;
    private final AiSensitiveDataMasker masker;
    private final AiAuditLogService auditLogService;
    private final UserContextResolver userContextResolver;
    private final AiToolCallContext toolCallContext;
    private final AiQueryNormalizer queryNormalizer = new AiQueryNormalizer();

    public ToolExecutorService(AiReadonlyQueryService readonlyQueryService,
                                 AiLogAnalysisService logAnalysisService,
                                 AiGeneratedSqlQueryService generatedSqlQueryService,
                                 AiSensitiveDataMasker masker,
                                AiAuditLogService auditLogService,
                                UserContextResolver userContextResolver,
                                AiToolCallContext toolCallContext) {
        this.readonlyQueryService = readonlyQueryService;
        this.logAnalysisService = logAnalysisService;
        this.generatedSqlQueryService = generatedSqlQueryService;
        this.masker = masker;
        this.auditLogService = auditLogService;
        this.userContextResolver = userContextResolver;
        this.toolCallContext = toolCallContext;
    }

    /**
     * 执行工具调用。
     *
     * @param userId      用户标识
     * @param permissions 用户权限列表
     * @param toolName    工具名称
     * @param arguments   工具参数
     * @return 标准化的执行结果 Map
     */
    public Map<String, Object> execute(String userId, List<String> permissions, String userCode,
                                       String roleCode, String customerId, String loginSessionId,
                                       String conversationId, String toolName, Map<String, Object> arguments) {
        if (!StringUtils.hasText(userId)) {
            return errorResult("未提供用户标识，无法执行工具调用");
        }
        if (!StringUtils.hasText(toolName)) {
            return errorResult("未指定工具名称");
        }

        List<String> safePermissions = permissions == null ? Collections.emptyList() : permissions;
        Map<String, Object> safeArgs = arguments == null ? Collections.emptyMap() : arguments;

        try {
            SseChatContext.setLoginIdAndPermissions(userId, safePermissions);
            SseChatContext.setUserCode(userCode);
            SseChatContext.setRoleCode(roleCode);
            SseChatContext.setCustomerId(customerId);
            SseChatContext.setLoginSessionId(loginSessionId);
            toolCallContext.begin();
            toolCallContext.setConversation(conversationId, userId, userCode);
            log.debug("执行工具调用 toolName={}, userId={}", toolName, maskUserId(userId));
        } catch (Exception e) {
            log.warn("设置用户上下文失败 userId={}", maskUserId(userId));
            return errorResult("设置用户上下文失败");
        }

        try {
            Map<String, Object> result;
            switch (toolName) {
                case "query_business_module":
                    result = executeQueryBusinessModule(safeArgs);
                    break;
                case "global_fuzzy_search":
                    result = executeGlobalFuzzySearch(safeArgs);
                    break;
                case "joined_business_query":
                    result = executeJoinedBusinessQuery(safeArgs);
                    break;
                case "query_dashboard":
                    result = executeQueryDashboard();
                    break;
                case "query_log_analysis":
                    result = safePermissions.contains("ai:log:analyze")
                            ? executeQueryLogAnalysis(safeArgs)
                            : errorResult("当前账号无权使用日志分析工具");
                    break;
                case "execute_readonly_sql":
                case "readonly_sql_analysis":
                    result = executeReadonlySqlAnalysis(safeArgs);
                    break;
                case "continue_cursor":
                    result = executeContinueCursor(userId, conversationId, safeArgs);
                    break;
                default:
                    result = errorResult("不支持的工具：" + toolName);
                    break;
            }

            String resultSummary = result.containsKey("success") ? String.valueOf(result.get("success")) : "unknown";
            String toolTarget = extractTarget(toolName, safeArgs);
            auditLogService.recordToolCall(StringUtils.hasText(conversationId) ? conversationId : INTERNAL_CONVERSATION_ID,
                    toolName, toolTarget,
                    "工具调用：" + toolName, resultSummary);

            return result;
        } catch (Exception e) {
            log.error("工具执行异常 toolName={} userId={}", toolName, maskUserId(userId), e);
            return errorResult("工具执行失败，请稍后重试或缩小查询范围");
        } finally {
            toolCallContext.snapshotAndClear();
            SseChatContext.clear();
        }
    }

    public Map<String, Object> execute(String userId, List<String> permissions, String toolName, Map<String, Object> arguments) {
        return execute(userId, permissions, "", "", "", "", "AI_INTERNAL", toolName, arguments);
    }

    // ---- 各工具执行方法 ----

    private Map<String, Object> executeQueryBusinessModule(Map<String, Object> args) {
        String module = stringArg(args, "module");
        String keyword = stringArg(args, "keyword");
        String startTime = stringArg(args, "startTime");
        String endTime = stringArg(args, "endTime");
        AiQueryNormalizer.NormalizedQuery normalized = queryNormalizer.normalize(module, keyword, startTime, endTime,
                String.join(" ", nullToEmpty(module), nullToEmpty(keyword)));

        if (!StringUtils.hasText(normalized.module())) {
            return errorResult("缺少必填参数 module");
        }
        AiReadonlyQueryResult result = readonlyQueryService.queryModule(
                normalized.module(),
                StringUtils.hasText(normalized.keyword()) ? normalized.keyword() : "",
                normalized.startTime(),
                normalized.endTime());
        return wrapQueryResult(result, normalized.module());
    }

    private Map<String, Object> executeGlobalFuzzySearch(Map<String, Object> args) {
        String keyword = stringArg(args, "keyword");
        String startTime = stringArg(args, "startTime");
        String endTime = stringArg(args, "endTime");

        if (!StringUtils.hasText(keyword)) {
            return errorResult("缺少必填参数 keyword");
        }

        AiQueryNormalizer.NormalizedQuery normalized = queryNormalizer.normalize(null, keyword, startTime, endTime, keyword);
        AiReadonlyQueryResult result = readonlyQueryService.globalSearch(
                StringUtils.hasText(normalized.keyword()) ? normalized.keyword() : keyword,
                normalized.startTime(),
                normalized.endTime());
        return wrapQueryResult(result, "全局模糊搜索");
    }

    private Map<String, Object> executeJoinedBusinessQuery(Map<String, Object> args) {
        String scene = stringArg(args, "scene");
        String keyword = stringArg(args, "keyword");
        String startTime = stringArg(args, "startTime");
        String endTime = stringArg(args, "endTime");

        if (!StringUtils.hasText(scene)) {
            return errorResult("缺少必填参数 scene");
        }
        if (!StringUtils.hasText(keyword)) {
            return errorResult("缺少必填参数 keyword");
        }

        AiQueryNormalizer.NormalizedQuery normalized = queryNormalizer.normalize(scene, keyword, startTime, endTime,
                String.join(" ", nullToEmpty(scene), nullToEmpty(keyword)));
        AiReadonlyQueryResult result = readonlyQueryService.joinedSearch(scene,
                StringUtils.hasText(normalized.keyword()) ? normalized.keyword() : keyword,
                normalized.startTime(),
                normalized.endTime());
        return wrapQueryResult(result, "业务联合查询");
    }

    private Map<String, Object> executeQueryDashboard() {
        AiReadonlyQueryResult result = readonlyQueryService.queryDashboard();
        return wrapQueryResult(result, "运营看板");
    }

    private Map<String, Object> executeQueryLogAnalysis(Map<String, Object> args) {
        String traceId = stringArg(args, "traceId");
        String operationId = stringArg(args, "operationId");
        String loginSessionId = stringArg(args, "loginSessionId");
        String userId = stringArg(args, "userId");
        String startTime = stringArg(args, "startTime");
        String endTime = stringArg(args, "endTime");

        AiLogAnalyzeRequest request = new AiLogAnalyzeRequest(
                traceId, operationId, loginSessionId, userId, null, startTime, endTime);
        AiLogAnalysisResponse response = logAnalysisService.analyze(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("summary", masker.mask(response.summary()));
        result.put("riskPoints", maskList(response.riskPoints()));
        result.put("suggestions", maskList(response.suggestions()));
        result.put("totalCount", response.timeline() != null ? response.timeline().size() : 0);

        if (response.relatedLogs() != null) {
            List<Map<String, Object>> maskedLogs = new ArrayList<>();
            for (Map<String, Object> log : response.relatedLogs()) {
                Map<String, Object> maskedLog = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : log.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String s) {
                        maskedLog.put(entry.getKey(), masker.mask(s));
                    } else {
                        maskedLog.put(entry.getKey(), value);
                    }
                }
                maskedLogs.add(maskedLog);
            }
            result.put("data", maskedLogs);
        } else {
            result.put("data", Collections.emptyList());
        }

        List<Map<String, Object>> timelineData = new ArrayList<>();
        if (response.timeline() != null) {
            for (AiLogTimelineItem item : response.timeline()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("time", masker.mask(item.time()));
                entry.put("operation", masker.mask(item.operation()));
                entry.put("uri", masker.mask(item.uri()));
                entry.put("method", item.method());
                entry.put("status", item.status());
                entry.put("costMs", item.costMs());
                entry.put("errorMessage", masker.mask(item.errorMessage()));
                timelineData.add(entry);
            }
        }
        result.put("timeline", timelineData);
        result.put("citation", Map.of(
                "source", "系统操作日志",
                "module", "operationLogs",
                "permissionChecked", "system:log:query"
        ));

        return result;
    }

    private Map<String, Object> executeReadonlySqlAnalysis(Map<String, Object> args) {
        String question = stringArg(args, "question");
        if (!StringUtils.hasText(question)) {
            question = stringArg(args, "sql");
        }

        if (!StringUtils.hasText(question)) {
            return errorResult("缺少必填参数 question");
        }

        try {
            AiGeneratedSqlQueryResult queryResult = generatedSqlQueryService.query(question);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", queryResult.executed());
            result.put("data", queryResult.records() != null ? maskRows(queryResult.records()) : Collections.emptyList());
            result.put("rows", queryResult.records() != null ? maskRows(queryResult.records()) : Collections.emptyList());
            result.put("totalCount", queryResult.records() != null ? queryResult.records().size() : 0);
            result.put("returnedCount", queryResult.records() != null ? queryResult.records().size() : 0);
            result.put("message", masker.mask(queryResult.message()));
            result.put("summary", masker.mask(queryResult.message()));
            result.put("citation", Map.of(
                    "source", "临时只读 SQL 查询",
                    "module", "generated_sql",
                    "permissionChecked", true
            ));
            return result;
        } catch (Exception e) {
            log.warn("临时 SQL 分析失败 reason={}", e.getMessage());
            return errorResult("临时 SQL 分析失败，请换一种描述后重试");
        }
    }

    private Map<String, Object> executeContinueCursor(String userId, String conversationId, Map<String, Object> args) {
        String cursorId = stringArg(args, "cursorId");

        if (!StringUtils.hasText(cursorId)) {
            return errorResult("缺少必填参数 cursorId");
        }

        try {
            String cursorConversationId = StringUtils.hasText(conversationId) ? conversationId : INTERNAL_CONVERSATION_ID;
            String currentUserId = userContextResolver.currentUserId();
            String currentUserCode = userContextResolver.currentUserCode();
            AiReadonlyQueryResult result = readonlyQueryService.queryCursor(
                    cursorId, cursorConversationId, currentUserId, currentUserCode);
            return wrapQueryResult(result, "查询游标续查");
        } catch (Exception e) {
            log.warn("游标继续查询失败 cursorId={} userId={}", cursorId, maskUserId(userId), e);
            return errorResult("游标查询失败，该查询结果可能已过期，请重新发起查询");
        }
    }

    // ---- 结果包装 ----

    private Map<String, Object> wrapQueryResult(AiReadonlyQueryResult result, String moduleName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", result.executed());
        map.put("data", result.rows() != null ? maskRows(result.rows()) : Collections.emptyList());
        map.put("rows", result.rows() != null ? maskRows(result.rows()) : Collections.emptyList());
        map.put("columns", result.columns() != null ? result.columns() : Collections.emptyList());
        map.put("totalCount", result.total() != null ? result.total() : 0);
        map.put("returnedCount", result.returnedCount() != null ? result.returnedCount() : 0);
        map.put("remainingCount", result.remainingCount() != null ? result.remainingCount() : 0);
        map.put("cursorId", masker.mask(result.cursorId()));
        map.put("hasMore", result.hasMore() != null && result.hasMore());
        map.put("nextPageHint", masker.mask(result.nextPageHint()));

        Map<String, Object> citation = new LinkedHashMap<>();
        citation.put("source", "业务数据查询");
        citation.put("module", masker.mask(moduleName));
        citation.put("permissionChecked", true);
        map.put("citation", citation);

        map.put("summary", masker.mask(result.answerContext()));
        return map;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<Map<String, Object>> maskRows(List<Map<String, Object>> rows) {
        if (rows == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> masked = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> maskedRow = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String s) {
                    maskedRow.put(entry.getKey(), masker.mask(s));
                } else {
                    maskedRow.put(entry.getKey(), value);
                }
            }
            masked.add(maskedRow);
        }
        return masked;
    }

    private List<String> maskList(List<String> items) {
        if (items == null) {
            return Collections.emptyList();
        }
        List<String> masked = new ArrayList<>();
        for (String item : items) {
            masked.add(masker.mask(item));
        }
        return masked;
    }

    // ---- 辅助方法 ----

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        String safeMessage = masker.mask(message);
        map.put("success", false);
        map.put("error", safeMessage);
        map.put("message", safeMessage);
        map.put("summary", safeMessage);
        map.put("data", Collections.emptyList());
        map.put("rows", Collections.emptyList());
        map.put("totalCount", 0);
        map.put("returnedCount", 0);
        return map;
    }

    private String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return String.valueOf(value);
    }

    private String extractTarget(String toolName, Map<String, Object> args) {
        if (args == null) {
            return toolName;
        }
        switch (toolName) {
            case "query_business_module": return stringArg(args, "module");
            case "global_fuzzy_search": return "全场景搜索";
            case "joined_business_query": return stringArg(args, "scene");
            case "query_dashboard": return "运营看板";
            case "query_log_analysis": return "日志分析";
            case "execute_readonly_sql":
            case "readonly_sql_analysis": return "临时SQL";
            case "continue_cursor": return "继续翻页";
            default: return toolName;
        }
    }

    private String maskUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return "anonymous";
        }
        return userId.length() > 8 ? userId.substring(0, 4) + "****" : userId;
    }
}
