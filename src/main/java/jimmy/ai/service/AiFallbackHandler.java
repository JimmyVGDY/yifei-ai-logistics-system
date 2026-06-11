package jimmy.ai.service;

import jimmy.ai.model.AiToolCall;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 助手兜底回答处理器 —— 模型不可用或工具调用失败时生成面向用户的友好提示。
 * <p>
 * 从 {@link AiAssistantService} 拆分，独立管理兜底策略：
 * <ol>
 *   <li>优先展示业务只读查询摘要（已格式化）</li>
 *   <li>取最后一条非系统工具结果作为回答</li>
 *   <li>所有工具都未匹配数据时给出明确引导</li>
 *   <li>无工具执行时提示检查 API Key 配置</li>
 * </ol>
 * <p>
 * 不做任何内部工具细节、权限码、模块名的外泄。
 */
@Component
public class AiFallbackHandler {

    /**
     * 模型不可用时生成面向用户的友好兜底提示。
     * <p>
     * 不做任何内部工具细节、权限码、模块名的外泄。
     * 仅展示业务数据实际匹配结果，无匹配时引导用户提供更精确的关键词。
     *
     * @param context   上下文摘要（知识库 + 工具调用结果）
     * @param toolCalls 已执行的工具调用列表
     * @return 面向用户的兜底回答
     */
    public String fallbackAnswer(String context, List<AiToolCall> toolCalls) {
        // 1. 提取业务只读查询的摘要（已经过格式化，直接面向用户）
        String businessResult = extractSection(context, "业务只读查询摘要：", true);
        if (businessResult != null) {
            return businessResult;
        }
        // 2. 如果模型调用中触发了工具，取最后一个非系统工具的结果
        for (int i = toolCalls.size() - 1; i >= 0; i--) {
            AiToolCall toolCall = toolCalls.get(i);
            if (isSystemTool(toolCall)) {
                continue;
            }
            String result = toolCall.result();
            if (StringUtils.hasText(result) && !result.contains("权限不足") && !result.contains("未匹配到记录") && !result.contains("共匹配 0")) {
                return result;
            }
        }
        // 3. 所有工具都未匹配到数据，给用户明确的引导
        if (!toolCalls.isEmpty()) {
            return "暂未找到相关业务数据。请提供更具体的关键词（如订单号、运单号、客户名称、手机号、司机姓名或日期范围），我会重新查找。";
        }
        // 4. 没有任何工具执行（极端异常情况）
        return "AI 模型暂时不可用，请稍后重试。如果问题持续，请检查 API Key 配置。";
    }

    /**
     * 从上下文字符串中提取指定标题后的内容，去除内部标签后仅保留面向用户的文本。
     *
     * @param context    上下文文本
     * @param label      要查找的标题标签
     * @param trimPrefix 是否去除标签前缀本身
     * @return 提取到的内容，未找到时返回 null
     */
    public String extractSection(String context, String label, boolean trimPrefix) {
        if (!StringUtils.hasText(context)) {
            return null;
        }
        int idx = context.indexOf(label);
        if (idx < 0) {
            return null;
        }
        String content = context.substring(idx + label.length());
        // 截取到下一个 section 标题
        int nextSection = content.indexOf("\nAI 工具调用摘要：");
        if (nextSection < 0) {
            nextSection = content.indexOf("\n已执行工具：");
        }
        if (nextSection < 0) {
            nextSection = content.indexOf("\nAI 长期记忆");
        }
        if (nextSection > 0) {
            content = content.substring(0, nextSection);
        }
        content = content.trim();
        if (trimPrefix && content.startsWith("业务只读查询摘要：")) {
            content = content.substring("业务只读查询摘要：".length()).trim();
        }
        return StringUtils.hasText(content) ? content : null;
    }

    /**
     * 判断工具调用是否为系统内部操作（长期记忆召回、日志排障等），这些不应该展示给用户。
     */
    public boolean isSystemTool(AiToolCall toolCall) {
        String name = toolCall.toolName();
        return "长期记忆召回".equals(name) || "日志排障".equals(name);
    }
}
