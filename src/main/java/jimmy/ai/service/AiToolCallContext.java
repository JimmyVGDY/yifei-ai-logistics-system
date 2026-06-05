package jimmy.ai.service;

import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiToolCall;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 工具调用上下文。
 * <p>
 * Spring AI 调用 @Tool 方法时，最终只会把工具返回文本交给模型继续组织答案。
 * 前端和审计日志还需要知道“调用了哪个工具、查了哪个模块、命中了多少数据”，
 * 因此这里用 ThreadLocal 在同一次 HTTP 请求内收集工具执行结果。
 */
@Component
public class AiToolCallContext {

    private final ThreadLocal<Holder> holder = new ThreadLocal<>();

    public void begin() {
        holder.set(new Holder());
    }

    public void record(AiReadonlyQueryResult result) {
        Holder current = holder.get();
        if (current == null || result == null || !result.executed()) {
            return;
        }
        current.citations.addAll(result.citations());
        current.toolCalls.addAll(result.toolCalls());
        if (result.answerContext() != null && !result.answerContext().isBlank()) {
            current.contexts.add(result.answerContext());
        }
    }

    public Snapshot snapshotAndClear() {
        Holder current = holder.get();
        holder.remove();
        if (current == null) {
            return new Snapshot(List.of(), List.of(), List.of());
        }
        return new Snapshot(List.copyOf(current.citations), List.copyOf(current.toolCalls), List.copyOf(current.contexts));
    }

    private static class Holder {
        private final List<AiCitation> citations = new ArrayList<>();
        private final List<AiToolCall> toolCalls = new ArrayList<>();
        private final List<String> contexts = new ArrayList<>();
    }

    public record Snapshot(List<AiCitation> citations, List<AiToolCall> toolCalls, List<String> contexts) {
    }
}
