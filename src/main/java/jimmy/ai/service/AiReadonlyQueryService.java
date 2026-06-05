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
    private static final List<SearchModule> GLOBAL_SEARCH_MODULES = List.of(
            new SearchModule("customers", "客户管理", "customer:query"),
            new SearchModule("orders", "运单管理", "order:query"),
            new SearchModule("waybills", "运单中心", "waybill:query"),
            new SearchModule("tasks", "运输任务", "task:query"),
            new SearchModule("tracks", "物流轨迹", "track:query"),
            new SearchModule("exceptions", "异常管理", "exception:query"),
            new SearchModule("fees", "费用结算", "fee:query")
    );

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
        return queryByIntent(intent, message);
    }

    public AiReadonlyQueryResult query(String message, String previousUserMessage) {
        AiGeneratedSqlQueryResult sqlQueryResult = generatedSqlQueryService.query(message);
        if (sqlQueryResult.executed()) {
            return new AiReadonlyQueryResult(true, sqlQueryResult.message(),
                    List.of(citation("临时只读 SQL 查询", sqlQueryResult.message())),
                    List.of(new AiToolCall("临时只读 SQL 查询", "关联查询", sqlQueryResult.message())));
        }
        if (isGlobalSearchRequest(message) && hasText(previousUserMessage)) {
            AiQueryIntent previousIntent = intentParser.parse(previousUserMessage);
            if (hasText(previousIntent.keyword())) {
                return globalSearch(previousIntent.keyword(), null);
            }
        }
        AiQueryIntent intent = intentParser.parse(message, previousUserMessage);
        return queryByIntent(intent, message);
    }

    private AiReadonlyQueryResult queryByIntent(AiQueryIntent intent, String message) {
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
            AiReadonlyQueryResult result = queryModule(intent);
            if (shouldFallbackToGlobalSearch(intent, result)) {
                return globalSearch(intent.keyword(), intent.module());
            }
            return result;
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

    /**
     * 客户名称、联系人姓名等纯关键词经常散落在订单、运单、异常等模块。
     * 单一客户模块查不到时，继续用同一关键词做只读跨模块召回，避免让用户反复指定页面。
     */
    private AiReadonlyQueryResult globalSearch(String keyword, String excludedModule) {
        if (!hasText(keyword)) {
            return AiReadonlyQueryResult.empty();
        }
        List<AiCitation> citations = new java.util.ArrayList<>();
        List<AiToolCall> toolCalls = new java.util.ArrayList<>();
        StringBuilder answer = new StringBuilder("已按关键词“").append(keyword).append("”进行全局只读查找。");
        long total = 0;
        int queriedModules = 0;

        for (SearchModule module : GLOBAL_SEARCH_MODULES) {
            if (module.module().equals(excludedModule)) {
                continue;
            }
            if (!StpUtil.hasPermission(module.permission())) {
                continue;
            }
            try {
                ModuleQueryDTO query = new ModuleQueryDTO();
                query.setPage(1);
                query.setPageSize(5);
                query.setKeyword(keyword);
                PageResult<ModuleRecordVO> page = logisticsRequirementService.modulePage(module.module(), query);
                queriedModules++;
                total += page.total();
                if (page.total() > 0) {
                    AiQueryIntent intent = new AiQueryIntent(
                            module.module(), module.moduleName(), module.permission(), keyword,
                            null, null, false, false, true
                    );
                    String summary = masker.mask(summaryService.moduleSummary(intent, page));
                    answer.append("\n\n").append(summary);
                    citations.add(citation(module.moduleName(), summary));
                    toolCalls.add(new AiToolCall("全局只读查找", module.moduleName(),
                            masker.mask(module.moduleName() + " 命中 " + page.total() + " 条记录。")));
                }
            } catch (IllegalStateException exception) {
                log.info("AI 全局只读查找跳过受限模块，module={}, reason={}", module.module(), exception.getMessage());
            } catch (RuntimeException exception) {
                log.warn("AI 全局只读查找模块失败，module={}, reason={}", module.module(), exception.getMessage());
            }
        }

        if (queriedModules == 0) {
            return simpleResult("全局只读查找", "业务模块", PERMISSION_DENIED_MESSAGE);
        }
        if (total == 0) {
            String message = "已在当前账号可访问的业务模块中按关键词“" + keyword + "”查找，暂未匹配到记录。";
            return simpleResult("全局只读查找", "业务模块", message);
        }
        String context = masker.mask(answer.toString());
        if (toolCalls.isEmpty()) {
            toolCalls.add(new AiToolCall("全局只读查找", "业务模块", "命中 " + total + " 条记录。"));
        }
        return new AiReadonlyQueryResult(true, context, citations, toolCalls);
    }

    private boolean shouldFallbackToGlobalSearch(AiQueryIntent intent, AiReadonlyQueryResult result) {
        return "customers".equals(intent.module())
                && hasText(intent.keyword())
                && result.answerContext().contains("共匹配 0 条记录");
    }

    private boolean isGlobalSearchRequest(String message) {
        if (!hasText(message)) {
            return false;
        }
        return message.contains("全局查找")
                || message.contains("全局搜索")
                || message.contains("全局查询")
                || message.contains("到处找")
                || message.contains("所有模块");
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

    private boolean hasText(String value) {
        return org.springframework.util.StringUtils.hasText(value);
    }

    private record SearchModule(String module, String moduleName, String permission) {
    }
}
