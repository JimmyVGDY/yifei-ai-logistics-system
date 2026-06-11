package jimmy.ai.model;

import java.util.List;

/**
 * Agent 单步执行记录 —— 记录一个迭代轮次中的模型思考和工具调用。
 *
 * @param iteration  第几轮迭代（从 1 开始）
 * @param thinking   模型思考过程文本
 * @param toolCalls  本轮调用的工具列表
 * @param summary    本轮操作摘要
 */
public record AgentStep(
        int iteration,
        String thinking,
        List<AiToolCall> toolCalls,
        String summary
) {
}
