package jimmy.ai.model;

import java.util.List;

/**
 * AI 回答证据校验结果。
 * <p>
 * 不与用户交互，只向管线返回修复指令。管线根据本结果决定：
 * 丢弃回答改用兜底、静默修正措辞、或原样放行。
 */
public record GroundingCheck(
        String answer,
        boolean discardOriginal,
        List<String> issues) {

    public static GroundingCheck passThrough(String answer) {
        return new GroundingCheck(answer, false, List.of());
    }

    public static GroundingCheck discard(List<String> issues) {
        return new GroundingCheck("", true, List.copyOf(issues));
    }

    public static GroundingCheck repaired(String answer, List<String> issues) {
        return new GroundingCheck(answer, false, List.copyOf(issues));
    }
}
