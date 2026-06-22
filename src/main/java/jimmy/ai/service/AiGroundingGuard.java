package jimmy.ai.service;

import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiDataResult;
import jimmy.ai.model.AiToolCall;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 回答证据校验器。
 * <p>
 * 这层不负责判断模型回答“好不好”，只处理高风险幻觉：
 * 没有任何可核验证据却声称查到系统数据，或者只返回了分页数据却声称已经完整列出。
 */
@Service
public class AiGroundingGuard {

    public String correctAnswer(String answer,
                                List<AiCitation> citations,
                                List<AiToolCall> toolCalls,
                                List<AiDataResult> dataResults) {
        if (!StringUtils.hasText(answer)) {
            return answer;
        }
        boolean hasEvidence = hasEvidence(citations, toolCalls, dataResults);
        if (!hasEvidence && looksLikeDataClaim(answer)) {
            return "我这次没有拿到可核验的业务查询结果，所以不能直接断言系统里有这些数据。"
                    + "请换一种描述重新查询，或指定要查询的模块、时间范围和关键词。";
        }
        String corrected = answer;
        if (hasPartialData(dataResults) && claimsFullResult(answer)) {
            corrected += "\n\n> 说明：本次只返回了分页结果，未完整展开全部数据。"
                    + "如需继续查看，请使用结果卡片里的“继续查看剩余数据”。";
        }
        return corrected;
    }

    private boolean hasEvidence(List<AiCitation> citations, List<AiToolCall> toolCalls, List<AiDataResult> dataResults) {
        return (citations != null && !citations.isEmpty())
                || (toolCalls != null && toolCalls.stream().anyMatch(call -> StringUtils.hasText(call.result())))
                || (dataResults != null && dataResults.stream().anyMatch(result -> result.rows() != null && !result.rows().isEmpty()));
    }

    private boolean hasPartialData(List<AiDataResult> dataResults) {
        if (dataResults == null) {
            return false;
        }
        for (AiDataResult result : dataResults) {
            if (result != null && Boolean.TRUE.equals(result.hasMore())) {
                return true;
            }
            if (result != null && result.total() > 0 && result.returnedCount() > 0 && result.returnedCount() < result.total()) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeDataClaim(String answer) {
        return containsAny(answer, "已查询", "查询结果", "系统中有", "共有", "命中", "记录", "数据库", "已找到");
    }

    private boolean claimsFullResult(String answer) {
        return containsAny(answer, "完整列出", "全部列出", "所有记录", "不要省略", "全部数据", "完整明细");
    }

    private boolean containsAny(String value, String... words) {
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
