package jimmy.ai.service;

import jimmy.ai.model.AiLogAnalysisResponse;
import jimmy.ai.model.AiLogAnalyzeRequest;
import jimmy.ai.model.AiLogTimelineItem;
import jimmy.logistics.model.ModuleQueryDTO;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.logistics.service.LogisticsRequirementService;
import jimmy.common.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 日志排障服务 —— 复用操作日志列表查询，不直接访问数据库。
 */
@Service
public class AiLogAnalysisService {

    private final LogisticsRequirementService logisticsRequirementService;
    private final AiSensitiveDataMasker masker;

    public AiLogAnalysisService(LogisticsRequirementService logisticsRequirementService,
                                AiSensitiveDataMasker masker) {
        this.logisticsRequirementService = logisticsRequirementService;
        this.masker = masker;
    }

    public AiLogAnalysisResponse analyze(AiLogAnalyzeRequest request) {
        ModuleQueryDTO query = new ModuleQueryDTO();
        query.setPage(1);
        query.setPageSize(50);
        query.setKeyword(keyword(request));
        query.setStartTime(request == null ? null : request.startTime());
        query.setEndTime(request == null ? null : request.endTime());
        PageResult<ModuleRecordVO> page = logisticsRequirementService.modulePage("operationLogs", query);

        List<Map<String, Object>> logs = new ArrayList<>();
        List<AiLogTimelineItem> timeline = new ArrayList<>();
        for (ModuleRecordVO record : page.records()) {
            Map<String, Object> row = new LinkedHashMap<>(record);
            logs.add(maskRow(row));
            timeline.add(new AiLogTimelineItem(
                    text(row.get("operation_time")),
                    text(row.get("operation")),
                    text(row.get("request_uri")),
                    text(row.get("request_method")),
                    text(row.get("operation_status")),
                    text(row.get("cost_ms")),
                    masker.mask(text(row.get("error_message")))
            ));
        }

        List<String> riskPoints = riskPoints(logs);
        List<String> suggestions = suggestions(logs, riskPoints);
        String summary = logs.isEmpty()
                ? "未查询到匹配的操作日志，请确认 traceId、operationId、loginSessionId、用户编号或时间范围是否正确。"
                : "共匹配到 " + logs.size() + " 条操作日志，其中风险点 " + riskPoints.size() + " 个。";
        return new AiLogAnalysisResponse(summary, timeline, riskPoints, suggestions, logs);
    }

    private String keyword(AiLogAnalyzeRequest request) {
        if (request == null) {
            return null;
        }
        for (String value : List.of(request.operationId(), request.traceId(), request.loginSessionId(), request.userId(), request.uri())) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Map<String, Object> maskRow(Map<String, Object> row) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String text && sensitiveDisplayField(key)) {
                masked.put(key, masker.mask(text));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    private boolean sensitiveDisplayField(String key) {
        String lower = key == null ? "" : key.toLowerCase();
        return lower.contains("username")
                || lower.contains("params")
                || lower.contains("error")
                || lower.contains("change")
                || lower.contains("agent");
    }

    private List<String> riskPoints(List<Map<String, Object>> logs) {
        List<String> risks = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            String status = text(log.get("operation_status"));
            String cost = text(log.get("cost_ms"));
            if ("FAILED".equalsIgnoreCase(status)) {
                risks.add("接口执行失败：" + text(log.get("operation")) + "，uri=" + text(log.get("request_uri")));
            }
            if (StringUtils.hasText(cost)) {
                try {
                    if (Long.parseLong(cost) > 1000) {
                        risks.add("接口耗时偏高：" + text(log.get("operation")) + "，costMs=" + cost);
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字耗时不参与风险判断。
                }
            }
        }
        return risks;
    }

    private List<String> suggestions(List<Map<String, Object>> logs, List<String> riskPoints) {
        if (logs.isEmpty()) {
            return List.of("扩大时间范围或改用用户编号、接口路径继续检索。");
        }
        if (riskPoints.isEmpty()) {
            return List.of("未发现失败或慢接口，可继续按 traceId 查看结构化日志中的中间件链路。");
        }
        return List.of(
                "优先查看失败节点的 errorMessage 和 requestUri。",
                "如果同一 traceId 下有多条失败记录，按时间顺序定位第一个失败节点。",
                "如果只是权限失败，检查角色权限和用户特殊权限是否一致。"
        );
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
