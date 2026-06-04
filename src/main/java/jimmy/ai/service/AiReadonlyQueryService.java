package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiToolCall;
import jimmy.logistics.model.LogisticsDashboardSummary;
import jimmy.logistics.model.ModuleQueryDTO;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.logistics.service.LogisticsRequirementService;
import jimmy.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 只读查询服务：统一复用现有业务查询能力。
 * <p>
 * 优先走普通模块白名单查询，只有用户明确提出统计、关联、连表等复杂只读分析时，
 * 才委托 {@link AiGeneratedSqlQueryService} 生成候选 SELECT，并继续由后端安全校验兜底。
 * </p>
 */
@Slf4j
@Service
public class AiReadonlyQueryService {

    private static final String PERMISSION_DENIED_MESSAGE = "当前账号权限不足，无法查询该类数据。如有需要，请联系系统管理员。";
    private static final String CUSTOMER_NOT_BOUND_MESSAGE = "当前账号未绑定客户档案，无法查询相关业务数据，请联系系统管理员。";
    private static final String QUERY_FAILED_MESSAGE = "查询暂时失败，请稍后重试或联系系统管理员。";
    private static final String WRITE_REFUSED_MESSAGE = "当前 AI 助手仅支持只读查询和信息整理，不能执行新增、修改、删除、导入、导出或上传操作。";

    private final AiQueryIntentParser intentParser;
    private final AiGeneratedSqlQueryService generatedSqlQueryService;
    private final LogisticsRequirementService logisticsRequirementService;
    private final AiQuerySummaryService summaryService;
    private final AiSensitiveDataMasker masker;

    public AiReadonlyQueryService(AiQueryIntentParser intentParser,
                                  AiGeneratedSqlQueryService generatedSqlQueryService,
                                  LogisticsRequirementService logisticsRequirementService,
                                  AiQuerySummaryService summaryService,
                                  AiSensitiveDataMasker masker) {
        this.intentParser = intentParser;
        this.generatedSqlQueryService = generatedSqlQueryService;
        this.logisticsRequirementService = logisticsRequirementService;
        this.summaryService = summaryService;
        this.masker = masker;
    }

    public AiReadonlyQueryResult query(String message) {
        AiGeneratedSqlQueryResult sqlQueryResult = generatedSqlQueryService.query(message);
        if (sqlQueryResult.executed()) {
            return new AiReadonlyQueryResult(true, sqlQueryResult.message(),
                    List.of(citation("临时只读 SQL 查询", sqlQueryResult.message())),
                    List.of(new AiToolCall("临时只读 SQL 查询", "关联查询", sqlQueryResult.message())));
        }
        AiQueryIntent intent = intentParser.parse(message);
        if (!intent.matched()) {
            return AiReadonlyQueryResult.empty();
        }
        if (intent.forbiddenWrite()) {
            return simpleResult("只读安全校验", "只读模式", WRITE_REFUSED_MESSAGE);
        }
        if (!StpUtil.hasPermission(intent.permission())) {
            log.info("AI 只读查询被权限拦截，module={}, permission={}", intent.module(), intent.permission());
            return simpleResult("业务数据查询", intent.moduleName(), PERMISSION_DENIED_MESSAGE);
        }
        try {
            if (intent.dashboard()) {
                return queryDashboard(intent);
            }
            return queryModule(intent);
        } catch (IllegalStateException exception) {
            log.info("AI 只读查询被数据范围拦截，module={}, reason={}", intent.module(), exception.getMessage());
            return simpleResult("业务数据查询", intent.moduleName(), CUSTOMER_NOT_BOUND_MESSAGE);
        } catch (RuntimeException exception) {
            log.warn("AI 只读查询失败，module={}, reason={}", intent.module(), exception.getMessage());
            return simpleResult("业务数据查询", intent.moduleName(), QUERY_FAILED_MESSAGE);
        }
    }

    private AiReadonlyQueryResult queryDashboard(AiQueryIntent intent) {
        LogisticsDashboardSummary summary = logisticsRequirementService.dashboardSummary();
        String answerContext = masker.mask(summaryService.dashboardSummary(summary));
        return new AiReadonlyQueryResult(true, answerContext,
                List.of(citation(intent.moduleName(), answerContext)),
                List.of(new AiToolCall("运营看板查询", intent.moduleName(), answerContext)));
    }

    private AiReadonlyQueryResult queryModule(AiQueryIntent intent) {
        ModuleQueryDTO query = new ModuleQueryDTO();
        query.setPage(1);
        query.setPageSize(10);
        query.setKeyword(intent.keyword());
        query.setStartTime(intent.startTime());
        query.setEndTime(intent.endTime());
        PageResult<ModuleRecordVO> page = logisticsRequirementService.modulePage(intent.module(), query);
        String summary = masker.mask(summaryService.moduleSummary(intent, page));
        String result = masker.mask(summaryService.queryConditionSummary(intent) + "，命中 " + page.total() + " 条记录。");
        return new AiReadonlyQueryResult(true, summary,
                List.of(citation(intent.moduleName(), summary)),
                List.of(new AiToolCall("业务数据查询", intent.moduleName(), result)));
    }

    private AiReadonlyQueryResult simpleResult(String toolName, String target, String message) {
        String safeMessage = masker.mask(message);
        return new AiReadonlyQueryResult(true, safeMessage,
                List.of(citation(target, safeMessage)),
                List.of(new AiToolCall(toolName, target, safeMessage)));
    }

    private AiCitation citation(String target, String snippet) {
        return new AiCitation("business-query", "业务数据查询", target, masker.mask(snippet));
    }
}
