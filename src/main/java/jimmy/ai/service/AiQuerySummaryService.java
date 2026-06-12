package jimmy.ai.service;

import jimmy.logistics.model.LogisticsDashboardSummary;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.common.model.PageResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import jimmy.ai.model.AiQueryIntent;

/**
 * AI 查询摘要服务：将数据库查询结果压缩成适合模型和前端阅读的中文摘要。
 */
@Component
public class AiQuerySummaryService {

    /**
     * 提供模型上下文的摘要上限。模型能同时看到这么多条记录，
     * 在回答中完整列出后再由前端 AiDataTable 承接结构化数据。
     */
    private static final int MAX_ROWS = 20;

    public String moduleSummary(AiQueryIntent intent, PageResult<ModuleRecordVO> page) {
        StringBuilder builder = new StringBuilder();
        builder.append("已查询").append(intent.moduleName()).append("，")
                .append(queryConditionSummary(intent)).append("。")
                .append("共匹配 ").append(page.total()).append(" 条记录。");
        if (page.records().isEmpty()) {
            builder.append("未找到符合条件的数据。");
            return builder.toString();
        }
        builder.append("\n请在回答中按 Markdown 表格完整列出以下所有记录，不要省略：");
        int count = 0;
        for (ModuleRecordVO record : page.records()) {
            if (count++ >= MAX_ROWS) {
                break;
            }
            builder.append("\n").append(count).append(". ").append(rowSummary(record));
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

    private String rowSummary(ModuleRecordVO record) {
        Map<String, Object> picked = pickReadableFields(record);
        StringJoiner joiner = new StringJoiner("，");
        for (Map.Entry<String, Object> entry : picked.entrySet()) {
            if (entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                joiner.add(entry.getKey() + "：" + entry.getValue());
            }
        }
        String text = joiner.toString();
        return text.isBlank() ? "记录摘要暂不可读" : text;
    }

    private Map<String, Object> pickReadableFields(ModuleRecordVO record) {
        Map<String, Object> result = new LinkedHashMap<>();
        putFirstPresent(result, record, "单号", "order_no", "waybill_no", "task_no");
        putFirstPresent(result, record, "客户", "customer_name", "contact_name");
        putFirstPresent(result, record, "名称", "cargo_name", "driver_name", "vehicle_no", "role_name", "real_name", "original_name");
        putFirstPresent(result, record, "状态", "statusLabel", "transport_statusLabel", "dispatch_statusLabel",
                "task_statusLabel", "exception_statusLabel", "payment_statusLabel");
        putFirstPresent(result, record, "位置", "current_location", "current_city");
        putFirstPresent(result, record, "时间", "created_at", "create_time", "operation_time", "report_time", "upload_time");
        return result;
    }

    private void putFirstPresent(Map<String, Object> result, ModuleRecordVO record, String label, String... keys) {
        for (String key : keys) {
            if (record.containsKey(key) && record.get(key) != null) {
                result.put(label, record.get(key));
                return;
            }
        }
    }

    private String nullToDash(String value) {
        return value == null ? "-" : value;
    }
}
