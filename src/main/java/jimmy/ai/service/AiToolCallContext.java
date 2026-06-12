package jimmy.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiToolCall;
import jimmy.ai.model.AiReadonlyQueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
 * 支持 SSE 流式推送：当设置了 OutputStream 时，每次工具调用完成后自动推送进度事件给前端。
 * <p>
 * 直接写 OutputStream 字节的方式替代了 SseEmitter.send()，彻底消除 Spring MVC
 * 异步 dispatch 的 send()/complete() 竞态问题。
 */
@Slf4j
@Component
public class AiToolCallContext {

    private final ThreadLocal<Holder> holder = new ThreadLocal<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 当前会话工具调用上限 */
    static final int MAX_TOOL_CALLS = 8;

    public void begin() {
        Holder existing = holder.get();
        Holder h = new Holder();
        // 如果外层已通过 begin(outputStream) 绑定通道，新 Holder 应继承 outputStream 以保证工具通知能被推送
        if (existing != null) {
            h.outputStream = existing.outputStream;
        }
        holder.set(h);
    }

    /**
     * 开启 SSE 流式推送模式。后续的每次工具调用都会自动向 outputStream 推送事件。
     *
     * @param outputStream HTTP 响应输出流（由 StreamingResponseBody 提供）
     */
    public void begin(OutputStream outputStream) {
        Holder h = new Holder();
        h.outputStream = outputStream;
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
        if (current == null || current.outputStream == null) {
            return;
        }
        sendEvent(current, "thinking", Map.of("message", "正在分析问题"));
    }

    /**
     * 推送"工具调用开始"事件。
     */
    public void notifyToolStart(String toolName, String target) {
        Holder current = holder.get();
        if (current == null || current.outputStream == null) {
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
        notifyToolResult(toolName, target, result, null, null);
    }

    /**
     * 推送"工具调用结果"事件，可附带结构化表格数据供前端渲染。
     *
     * @param rows    查询返回的数据行（key 为字段名，value 为字段值）；无表格数据时传 null
     * @param columns 字段名列表（保持顺序）；无表格数据时传 null
     */
    public void notifyToolResult(String toolName, String target, String result,
                                  List<Map<String, Object>> rows, List<String> columns) {
        Holder current = holder.get();
        if (current == null || current.outputStream == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolName", nullToEmpty(toolName));
        data.put("target", nullToEmpty(target));
        data.put("result", nullToEmpty(result));
        data.put("toolCallCount", current.callCount);
        data.put("maxToolCalls", MAX_TOOL_CALLS);
        data.put("elapsedMs", System.currentTimeMillis() - current.startTime);
        if (rows != null && !rows.isEmpty()) {
            data.put("rows", rows);
            data.put("columns", columns == null ? List.of() : columns);
        }
        sendEvent(current, "tool_result", data);
    }

    /**
     * 推送"流式完成"事件，携带最终答案。
     * <p>
     * 事件发送后立即 flush 确保数据到达客户端。不再调用 complete()——
     * StreamingResponseBody lambda 返回后 Spring MVC 会自动关闭输出流。
     */
    public void notifyDone(String answer, List<AiCitation> citations, List<AiToolCall> toolCalls) {
        notifyDone(null, answer, citations, toolCalls);
    }

    public void notifyDone(String conversationId, String answer, List<AiCitation> citations, List<AiToolCall> toolCalls) {
        Holder current = holder.get();
        if (current == null || current.outputStream == null) {
            log.warn("SSE notifyDone 跳过（holder 为空或 outputStream 为 null），holder={}", current);
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "done");
        data.put("conversationId", nullToEmpty(conversationId));
        data.put("answer", nullToEmpty(answer));
        data.put("elapsedMs", System.currentTimeMillis() - current.startTime);
        data.put("citationCount", citations == null ? 0 : citations.size());
        data.put("toolCallCount", toolCalls == null ? 0 : toolCalls.size());
        log.info("SSE notifyDone 准备推送，answerLength={}", answer == null ? 0 : answer.length());
        sendEvent(current, "done", data);
    }

    /**
     * 推送错误事件。
     */
    public void notifyError(String errorMessage) {
        Holder current = holder.get();
        if (current == null || current.outputStream == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "error");
        data.put("message", nullToEmpty(errorMessage));
        data.put("elapsedMs", System.currentTimeMillis() - current.startTime);
        sendEvent(current, "error", data);
    }

    /**
     * 推送"聊天超时"事件，使用对用户友好的提示语。
     */
    public void notifyTimeout() {
        notifyError("系统响应超时，请稍后重试");
    }

    /**
     * 直接写 SSE 字节到 OutputStream，同步 + flush，消除 SseEmitter.send() 的异步 dispatch 竞态。
     * <p>
     * SSE 格式：
     * <pre>
     * event:&lt;name&gt;\n
     * data:&lt;json&gt;\n
     * \n
     * </pre>
     */
    private void sendEvent(Holder current, String name, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            // SSE 要求 data 中的 \n 展开为多行 data: 前缀（标准 SSE 多行数据格式）
            String[] jsonLines = json.split("\n", -1);
            StringBuilder sse = new StringBuilder(128 + json.length());
            sse.append("event:").append(name).append("\n");
            for (String line : jsonLines) {
                sse.append("data:").append(line).append("\n");
            }
            sse.append("\n"); // 空行分隔事件
            byte[] bytes = sse.toString().getBytes(StandardCharsets.UTF_8);
            synchronized (current.outputStream) {
                current.outputStream.write(bytes);
                current.outputStream.flush();
            }
        } catch (IOException e) {
            log.warn("SSE 事件写入失败，event={}, reason={}", name, e.getMessage());
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
        /** 直接 HTTP 响应输出流，不用 SseEmitter 避免异步 dispatch 竞态 */
        private OutputStream outputStream;
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
