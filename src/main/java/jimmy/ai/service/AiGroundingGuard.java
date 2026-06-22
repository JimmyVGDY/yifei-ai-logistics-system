package jimmy.ai.service;

import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiDataResult;
import jimmy.ai.model.AiToolCall;
import jimmy.ai.model.GroundingCheck;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 回答证据校验器。
 * <p>
 * 不做用户可见的提示，只向管线返回修复指令：
 * <ul>
 *   <li>没有证据却声称查到数据 → 标记丢弃，管线改用兜底回答</li>
 *   <li>只有分页数据却声称完整列出 → 静默替换完整性措辞</li>
 *   <li>正常回答 → 原样放行</li>
 * </ul>
 * 整个检测→判断→修复过程对用户完全无感。
 */
@Service
public class AiGroundingGuard {

    /**
     * 校验回答的证据支撑，返回修复指令而非用户提示语。
     */
    public GroundingCheck check(String answer,
                                List<AiCitation> citations,
                                List<AiToolCall> toolCalls,
                                List<AiDataResult> dataResults) {
        if (!StringUtils.hasText(answer)) {
            return GroundingCheck.passThrough(answer);
        }

        boolean hasEvidence = hasEvidence(citations, toolCalls, dataResults);
        boolean partialData = hasPartialData(dataResults);
        boolean dataClaim = looksLikeDataClaim(answer);
        boolean fullClaim = claimsFullResult(answer);

        List<String> issues = new ArrayList<>();
        String result = answer;

        // 无证据但声称查到数据 → 丢弃，管线用兜底回答替代，用户无感
        if (!hasEvidence && dataClaim) {
            issues.add("UNSUPPORTED_DATA_CLAIM");
            return GroundingCheck.discard(issues);
        }

        // 仅分页数据却声称完整列出 → 静默替换完整性措辞
        if (partialData && fullClaim) {
            issues.add("PARTIAL_DATA_AS_FULL");
            result = toneDownCompletenessClaims(answer);
        }

        if (issues.isEmpty()) {
            return GroundingCheck.passThrough(answer);
        }
        return GroundingCheck.repaired(result, issues);
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
            if (result != null && (result.hasMore()
                    || (result.total() > 0 && result.returnedCount() > 0 && result.returnedCount() < result.total()))) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeDataClaim(String answer) {
        // 否定句不判为幻觉数据声称
        if (containsAny(answer, "没有查到", "未找到", "不存在相关", "无相关记录", "未查询到")) {
            return false;
        }
        return containsAny(answer, "已查询", "查询结果", "系统中有", "共有", "命中", "记录", "数据库", "已找到");
    }

    private boolean claimsFullResult(String answer) {
        return containsAny(answer, "完整列出", "全部列出", "所有记录", "不要省略", "全部数据", "完整明细");
    }

    /**
     * 静默替换完整性措辞，不追加任何系统提示。
     * 前端数据卡片已有"继续查看剩余 N 条"按钮，用户通过 UI 自然感知分页。
     */
    private String toneDownCompletenessClaims(String answer) {
        return answer
                .replace("完整列出", "列出")
                .replace("全部列出", "列出")
                .replace("所有记录", "查询到的记录")
                .replace("全部数据", "当前数据")
                .replace("完整明细", "明细");
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
