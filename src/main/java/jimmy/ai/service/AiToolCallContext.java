package jimmy.ai.service;

import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI 工具调用上下文。
 * <p>
 * Spring AI 调用 @Tool 方法时，最终只会把工具返回文本交给模型继续组织答案。
 * 前端和审计日志还需要知道"调用了哪个工具、查了哪个模块、命中了多少数据"，
 * 因此这里用 ThreadLocal 在同一次 HTTP 请求内收集工具执行结果。
 * <p>
 * 支持 SSE 流式推送：当设置了 SseEmitter 时，每次工具调用完成后自动推送进度事件给前端。
 */
@Slf4j
@Component
public class AiToolCallContext {

    private final ThreadLocal<Holder> holder = new ThreadLocal<>();

    /** 当前会话工具调用上限 */
    static final int MAX_TOOL_CALLS = 8;

    public void begin() {
        Holder existing = holder.get();
        Holder h = new Holder();
        // 如果外层已通过 begin(emitter) 绑定 SSE 通道，新 Holder 应继承 emitter 以保证工具通知能被推送
        if (existing != null) {
            h.emitter = existing.emitter;
        }
        holder.set(h);
    }

    /**
     * 开启 SSE 流式推送模式。后续的每次工具调用都会自动向 emitter 推送事件。
     */
    public void begin(SseEmitter emitter) {
        Holder h = new Holder();
        h.emitter = emitter;
        holder.set(h);
    }

    /**
     * 记录本次工具调用，返回当前累计调用次数。超过上限时抛出异常让 LLM 用已有信息回答。
     */
    public int incrementAndCheck() {
        Holder current = holder.get();
        if (current == null) {
            return 0;
        }
        int count = ++current.callCount;
        if (count > MAX_TOOL_CALLS) {
            throw new ToolCallLimitExceededException("已达到单次对话工具调用上限（" + MAX_TOOL_CALLS + "次），请基于已查询到的数据直接回答。");
        }
        return count;
    }

    /**
     * 推送"思考中"事件，告知前端 AI 正在处理。
     */
    public void notifyThinking() {
        Holder current = holder.get();
        if (current == null || current.emitter == null) {
            return;
        }
        sendEvent(current, "thinking", Map.of("message", "正在分析问题"));
    }

    /**
     * 推送"工具调用开始"事件。
     */
    public void notifyToolStart(String toolName, String target) {
        Holder current = holder.get();
        if (current == null || current.emitter == null) {
            return;
        }
        sendEvent(current, "tool_start", Map.of(
                "toolName", nullToEmpty(toolName),
                "target", nullToEmpty(target),
                "toolCallCount", current.callCount,
                "maxToolCalls", MAX_TOOL_CALLS,
                "elapsedMs", System.currentTimeMillis() - current.startTime
        ));
    }

    /**
     * 推送"工具调用结果"事件。
     */
    public void notifyToolResult(String toolName, String target, String result) {
        Holder current = holder.get();
        if (current == null || current.emitter == null) {
            return;
        }
        sendEvent(current, "tool_result", Map.of(
                "toolName", nullToEmpty(toolName),
                "target", nullToEmpty(target),
                "result", nullToEmpty(result),
                "toolCallCount", current.callCount,
                "maxToolCalls", MAX_TOOL_CALLS,
                "elapsedMs", System.currentTimeMillis() - current.startTime
        ));
    }

    /**
     * 推送"流式完成"事件，携带最终答案。
     */
    public void notifyDone(String answer, List<AiCitation> citations, List<AiToolCall> toolCalls) {
        Holder current = holder.get();
        if (current == null || current.emitter == null) {
            log.warn("SSE notifyDone 跳过（holder 为空或 emitter 为 null），holder={}", current);
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "done");
        data.put("answer", nullToEmpty(answer));
        data.put("elapsedMs", System.currentTimeMillis() - current.startTime);
        data.put("citationCount", citations == null ? 0 : citations.size());
        data.put("toolCallCount", toolCalls == null ? 0 : toolCalls.size());
        log.info("SSE notifyDone 准备推送，answerLength={}", answer == null ? 0 : answer.length());
        sendEvent(current, "done", data);
        // 等待 200ms 确保 SSE 事件已刷新到客户端，避免 send()/complete() 竞态导致前端收不到 done 事件
        sleepBeforeComplete();
        current.emitter.complete();
    }

    /**
     * 推送错误事件并关闭 SSE 连接。
     */
    public void notifyError(String errorMessage) {
        Holder current = holder.get();
        if (current == null || current.emitter == null) {
            return;
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", "error");
            data.put("message", nullToEmpty(errorMessage));
            data.put("elapsedMs", System.currentTimeMillis() - current.startTime);
            sendEvent(current, "error", data);
        } finally {
            // 使用 complete() 而非 completeWithError()，避免异常传播到 GlobalExceptionHandler
            // GlobalExceptionHandler 收到 completeWithError() 会尝试写 JSON 到 text/event-stream，导致二次报错
            sleepBeforeComplete();
            current.emitter.complete();
        }
    }

    /**
     * 推送"聊天超时"事件，使用对用户友好的提示语。
     */
    public void notifyTimeout() {
        notifyError("系统响应超时，请稍后重试");
    }

    private void sendEvent(Holder current, String name, Object data) {
        try {
            current.emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.warn("SSE 事件发送失败，event={}, reason={}", name, e.getMessage());
        }
    }

    /**
     * 短暂等待以确保 SSE 事件已通过 Spring MVC 异步分派刷写到客户端。
     * 避免 SseEmitter.send() 与 complete() 的竞态条件。
     */
    private void sleepBeforeComplete() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        private SseEmitter emitter;
        private int callCount = 0;
        private final long startTime = System.currentTimeMillis();
        private final List<AiCitation> citations = new ArrayList<>();
        private final List<AiToolCall> toolCalls = new ArrayList<>();
        private final List<String> contexts = new ArrayList<>();
    }

    public record Snapshot(List<AiCitation> citations, List<AiToolCall> toolCalls, List<String> contexts) {
    }

    public static class ToolCallLimitExceededException extends RuntimeException {
        public ToolCallLimitExceededException(String message) {
            super(message);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
