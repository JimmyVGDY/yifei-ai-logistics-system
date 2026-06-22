package jimmy.ai.service;

import jimmy.ai.model.AiDataResult;
import jimmy.ai.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGroundingGuardTest {

    private final AiGroundingGuard groundingGuard = new AiGroundingGuard();

    @Test
    void dataClaimWithoutEvidenceShouldBeReplacedBySafeMessage() {
        String answer = "已查询系统数据库，共有 10 条记录。";

        String corrected = groundingGuard.correctAnswer(answer, List.of(), List.of(), List.of());

        assertTrue(corrected.contains("没有拿到可核验的业务查询结果"));
    }

    @Test
    void partialDataShouldAppendPaginationWarningWhenAnswerClaimsFullResult() {
        AiDataResult result = new AiDataResult(
                "业务数据查询",
                "运单中心",
                "命中 12 条",
                List.of("运单号"),
                List.of(Map.of("运单号", "WB001"), Map.of("运单号", "WB002")),
                "cursor-1",
                12L,
                2,
                10L,
                true,
                "继续查看剩余数据"
        );

        String corrected = groundingGuard.correctAnswer(
                "已完整列出所有记录。",
                List.of(),
                List.of(new AiToolCall("业务数据查询", "运单中心", "命中 12 条")),
                List.of(result)
        );

        assertTrue(corrected.contains("本次只返回了分页结果"));
    }

    @Test
    void groundedNormalAnswerShouldRemainUnchanged() {
        String answer = "根据查询结果，运输任务中有 1 条异常记录。";

        String corrected = groundingGuard.correctAnswer(
                answer,
                List.of(),
                List.of(new AiToolCall("业务数据查询", "运输任务", "命中 1 条")),
                List.of()
        );

        assertEquals(answer, corrected);
    }
}
