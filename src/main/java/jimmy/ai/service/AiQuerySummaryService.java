package jimmy.ai.service;

import jimmy.logistics.model.LogisticsDashboardSummary;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.common.model.PageResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import jimmy.ai.model.AiQueryIntent;

/**
 * AI 查询摘要服务：将数据库查询结果压缩成适合模型和前端阅读的中文摘要。
 * <p>
 * 明细数据统一交给前端结构化表格和查询游标展示，这里只生成摘要。
 * 这样可以避免模型把几百条记录写进聊天气泡，导致页面过长、数据遗漏或重复展示。
 */
@Component
public class AiQuerySummaryService {

    public String moduleSummary(AiQueryIntent intent, PageResult<ModuleRecordVO> page) {
        StringBuilder builder = new StringBuilder();
        builder.append("已查询").append(intent.moduleName()).append("，")
                .append(queryConditionSummary(intent)).append("。")
                .append("共匹配 ").append(page.total()).append(" 条记录。");
        if (page.records().isEmpty()) {
            builder.append("未找到符合条件的数据。");
            return builder.toString();
        }
        int previewCount = page.records().size();
        long remaining = Math.max(0, page.total() - previewCount);
        builder.append("本次已返回前 ").append(previewCount).append(" 条结构化记录。");
        if (remaining > 0) {
            builder.append("还有 ").append(remaining).append(" 条记录可通过结果卡片继续分页查看。");
        } else {
            builder.append("已返回当前查询范围内的全部可展示记录。");
        }
        return builder.toString();
    }

    public String dashboardSummary(LogisticsDashboardSummary summary) {
        return "运营看板摘要：今日订单 " + summary.getTodayOrders()
                + "，待调度 " + summary.getWaitDispatchOrders()
                + "，运输中 " + summary.getInTransitOrders()
                + "，异常单 " + summary.getExceptionOrders()
                + "，本月收入 " + summary.getMonthIncome() + "。";
    }

    public String queryConditionSummary(AiQueryIntent intent) {
        List<String> parts = new ArrayList<>();
        if (intent.keyword() != null) {
            parts.add("关键词“" + intent.keyword() + "”");
        }
        if (intent.startTime() != null || intent.endTime() != null) {
            parts.add("时间范围“" + nullToDash(intent.startTime()) + " 至 " + nullToDash(intent.endTime()) + "”");
        }
        return parts.isEmpty() ? "默认最近数据" : String.join("，", parts);
    }

    private String nullToDash(String value) {
        return value == null ? "-" : value;
    }
}
