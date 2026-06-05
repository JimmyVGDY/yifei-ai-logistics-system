package jimmy.ai.service;

import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiToolCall;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI 业务查询工具集。
 * <p>
 * 这些工具只开放只读能力。模型负责选择工具和传递自然语言参数，
 * 但真正执行前仍由 {@link AiReadonlyQueryService} 做权限、数据范围、白名单和脱敏兜底。
 * <p>
 * 每次工具调用会通过 {@link AiToolCallContext} 推送 SSE 事件给前端，
 * 用于展示实时进度（tool_start / tool_result）。
 */
@Component
public class AiBusinessQueryTools {

    private final AiReadonlyQueryService readonlyQueryService;
    private final AiGeneratedSqlQueryService generatedSqlQueryService;
    private final AiToolCallContext toolCallContext;

    public AiBusinessQueryTools(AiReadonlyQueryService readonlyQueryService,
                                AiGeneratedSqlQueryService generatedSqlQueryService,
                                AiToolCallContext toolCallContext) {
        this.readonlyQueryService = readonlyQueryService;
        this.generatedSqlQueryService = generatedSqlQueryService;
        this.toolCallContext = toolCallContext;
    }

    @Tool(description = "查询单个物流业务模块。只读工具，可查订单、运单、客户、调度、任务、轨迹、司机、车辆、异常、费用、用户、角色、文件、操作日志。")
    public String queryBusinessModule(
            @ToolParam(description = "模块中文名或编码，例如订单管理、运单中心、客户管理、异常管理、费用结算、操作日志") String module,
            @ToolParam(description = "关键词，例如客户名、订单号、运单号、司机名、车牌号、地址、状态；没有关键词时传空字符串") String keyword,
            @ToolParam(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String startTime,
            @ToolParam(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String endTime) {
        String target = nullToEmpty(module);
        toolCallContext.incrementAndCheck();
        toolCallContext.notifyToolStart("业务数据查询", target);
        AiReadonlyQueryResult result = readonlyQueryService.queryModule(module, keyword, blankToNull(startTime), blankToNull(endTime));
        toolCallContext.record(result);
        toolCallContext.notifyToolResult("业务数据查询", target, toolResultSummary(result));
        return result.answerContext();
    }

    @Tool(description = "全场景模糊搜索。用户只输入短词、姓名、车牌、地址、货物名或不知道查哪个模块时使用。")
    public String globalFuzzySearch(
            @ToolParam(description = "模糊搜索关键词，例如陈土豆、沪A12345、天盈广场、生气啦、待处理") String keyword,
            @ToolParam(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String startTime,
            @ToolParam(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String endTime) {
        toolCallContext.incrementAndCheck();
        toolCallContext.notifyToolStart("全场景模糊搜索", "业务模块");
        AiReadonlyQueryResult result = readonlyQueryService.globalSearch(keyword, blankToNull(startTime), blankToNull(endTime));
        toolCallContext.record(result);
        toolCallContext.notifyToolResult("全场景模糊搜索", "业务模块", toolResultSummary(result));
        return result.answerContext();
    }

    @Tool(description = "业务联合查询。用户提出客户全貌、订单完整链路、司机任务链路、车辆任务链路、异常影响等跨模块问题时使用。")
    public String joinedBusinessQuery(
            @ToolParam(description = "联合查询场景，可传 customer、order、driver、vehicle、exception、business 或中文场景") String scene,
            @ToolParam(description = "关键词，例如客户名、订单号、司机名、车牌号、异常描述；没有关键词时传空字符串") String keyword,
            @ToolParam(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String startTime,
            @ToolParam(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String endTime) {
        String target = nullToEmpty(scene);
        toolCallContext.incrementAndCheck();
        toolCallContext.notifyToolStart("业务联合查询", target);
        AiReadonlyQueryResult result = readonlyQueryService.joinedSearch(scene, keyword, blankToNull(startTime), blankToNull(endTime));
        toolCallContext.record(result);
        toolCallContext.notifyToolResult("业务联合查询", target, toolResultSummary(result));
        return result.answerContext();
    }

    @Tool(description = "查询运营看板聚合数据，例如今日订单、待调度、运输中、异常数、收入趋势等。")
    public String queryDashboard() {
        toolCallContext.incrementAndCheck();
        toolCallContext.notifyToolStart("运营看板查询", "运营看板");
        AiReadonlyQueryResult result = readonlyQueryService.queryDashboard();
        toolCallContext.record(result);
        toolCallContext.notifyToolResult("运营看板查询", "运营看板", toolResultSummary(result));
        return result.answerContext();
    }

    @Tool(description = "复杂只读统计分析。只用于统计、排名、汇总、关联、连表等分析问题，禁止写操作。")
    public String readonlySqlAnalysis(
            @ToolParam(description = "用户原始统计或关联分析问题") String question) {
        toolCallContext.incrementAndCheck();
        toolCallContext.notifyToolStart("只读SQL分析", "关联查询");
        AiGeneratedSqlQueryResult sqlResult = generatedSqlQueryService.query(question);
        AiReadonlyQueryResult result = new AiReadonlyQueryResult(
                sqlResult.executed(),
                sqlResult.message(),
                sqlResult.executed() ? List.of(new AiCitation("business-query", "业务数据查询", "临时只读 SQL 查询", sqlResult.message())) : List.of(),
                sqlResult.executed() ? List.of(new AiToolCall("临时只读 SQL 查询", "关联查询", sqlResult.message())) : List.of()
        );
        toolCallContext.record(result);
        toolCallContext.notifyToolResult("只读SQL分析", "关联查询", toolResultSummary(result));
        return sqlResult.message();
    }

    /**
     * 从查询结果中提取简短的摘要，用于前端进度展示。
     * 例如："命中 3 条记录"、"权限不足"。
     */
    private String toolResultSummary(AiReadonlyQueryResult result) {
        if (result == null || result.toolCalls() == null || result.toolCalls().isEmpty()) {
            return "查询完成";
        }
        return result.toolCalls().get(0).result();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
