package jimmy.ai.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI 业务查询工具集。
 * <p>
 * 这些工具只开放只读能力。模型负责选择工具和传递自然语言参数，
 * 但真正执行前仍由 {@link AiReadonlyQueryService} 做权限、数据范围、白名单和脱敏兜底。
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
        AiReadonlyQueryResult result = readonlyQueryService.queryModule(module, keyword, blankToNull(startTime), blankToNull(endTime));
        toolCallContext.record(result);
        return result.answerContext();
    }

    @Tool(description = "全场景模糊搜索。用户只输入短词、姓名、车牌、地址、货物名或不知道查哪个模块时使用。")
    public String globalFuzzySearch(
            @ToolParam(description = "模糊搜索关键词，例如陈土豆、沪A12345、天盈广场、生气啦、待处理") String keyword,
            @ToolParam(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String startTime,
            @ToolParam(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String endTime) {
        AiReadonlyQueryResult result = readonlyQueryService.globalSearch(keyword, blankToNull(startTime), blankToNull(endTime));
        toolCallContext.record(result);
        return result.answerContext();
    }

    @Tool(description = "业务联合查询。用户提出客户全貌、订单完整链路、司机任务链路、车辆任务链路、异常影响等跨模块问题时使用。")
    public String joinedBusinessQuery(
            @ToolParam(description = "联合查询场景，可传 customer、order、driver、vehicle、exception、business 或中文场景") String scene,
            @ToolParam(description = "关键词，例如客户名、订单号、司机名、车牌号、异常描述；没有关键词时传空字符串") String keyword,
            @ToolParam(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String startTime,
            @ToolParam(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss；没有时间范围时传空字符串") String endTime) {
        AiReadonlyQueryResult result = readonlyQueryService.joinedSearch(scene, keyword, blankToNull(startTime), blankToNull(endTime));
        toolCallContext.record(result);
        return result.answerContext();
    }

    @Tool(description = "查询运营看板聚合数据，例如今日订单、待调度、运输中、异常数、收入趋势等。")
    public String queryDashboard() {
        AiReadonlyQueryResult result = readonlyQueryService.queryDashboard();
        toolCallContext.record(result);
        return result.answerContext();
    }

    @Tool(description = "复杂只读统计分析。只用于统计、排名、汇总、关联、连表等分析问题，禁止写操作。")
    public String readonlySqlAnalysis(
            @ToolParam(description = "用户原始统计或关联分析问题") String question) {
        AiGeneratedSqlQueryResult sqlResult = generatedSqlQueryService.query(question);
        AiReadonlyQueryResult result = new AiReadonlyQueryResult(
                sqlResult.executed(),
                sqlResult.message(),
                sqlResult.executed() ? java.util.List.of(new jimmy.ai.model.AiCitation("business-query", "业务数据查询", "临时只读 SQL 查询", sqlResult.message())) : java.util.List.of(),
                sqlResult.executed() ? java.util.List.of(new jimmy.ai.model.AiToolCall("临时只读 SQL 查询", "关联查询", sqlResult.message())) : java.util.List.of()
        );
        toolCallContext.record(result);
        return sqlResult.message();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
