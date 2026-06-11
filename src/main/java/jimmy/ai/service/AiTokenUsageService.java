package jimmy.ai.service;

import jimmy.ai.entity.AiTokenUsage;
import jimmy.ai.mapper.AiTokenUsageMapper;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI Token 用量追踪服务 —— 记录每次模型调用的 Token 消耗并支持成本统计。
 * <p>
 * 每次 {@link AiModelGateway} 调用模型后自动记录，异步写入不阻塞主流程。
 * 支持按模型、用途、时间范围汇总查询，便于成本管控和异常用量发现。
 */
@Slf4j
@Service
public class AiTokenUsageService {

    /**
     * 模型单价（美元/百万 Token），用于费用估算。
     * 未在列表中的模型按 gpt-4o-mini 价格计费。
     */
    private static final Map<String, BigDecimal> MODEL_PRICE_PER_MILLION = Map.of(
            "gpt-4o-mini", new BigDecimal("0.60"),
            "gpt-4o", new BigDecimal("5.00"),
            "gpt-4-turbo", new BigDecimal("10.00"),
            "deepseek-chat", new BigDecimal("0.28"),
            "deepseek-reasoner", new BigDecimal("0.55")
    );
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("0.60");
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final AiTokenUsageMapper tokenUsageMapper;
    private final CompactSnowflakeIdGenerator idGenerator;

    public AiTokenUsageService(AiTokenUsageMapper tokenUsageMapper,
                                CompactSnowflakeIdGenerator idGenerator) {
        this.tokenUsageMapper = tokenUsageMapper;
        this.idGenerator = idGenerator;
    }

    /**
     * 记录一次模型调用的 Token 消耗。
     * <p>
     * 写入失败不会抛出异常，仅记录日志，确保不影响主业务。
     *
     * @param modelName       模型名称
     * @param purpose         调用用途（chat / sql_generate / sql_self_check / sql_repair / memory_extract）
     * @param promptTokens    输入 Token 数
     * @param completionTokens 输出 Token 数
     * @param userId          调用用户 ID
     * @param userCode        调用用户业务编号
     * @param conversationId  关联的 AI 会话 ID（可选）
     * @param modelBaseUrl    模型 API 地址
     * @param durationMs      调用耗时（毫秒）
     */
    public void record(String modelName, String purpose, int promptTokens, int completionTokens,
                       String userId, String userCode, String conversationId,
                       String modelBaseUrl, long durationMs) {
        try {
            AiTokenUsage usage = new AiTokenUsage();
            usage.setId(idGenerator.nextId());
            usage.setModelName(modelName);
            usage.setPurpose(purpose);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(promptTokens + completionTokens);
            usage.setUserId(userId);
            usage.setUserCode(userCode);
            usage.setConversationId(conversationId);
            usage.setEstimatedCost(estimateCost(modelName, promptTokens, completionTokens));
            usage.setModelBaseUrl(modelBaseUrl);
            usage.setDurationMs(durationMs);
            usage.setCreatedAt(LocalDateTime.now());
            tokenUsageMapper.insert(usage);
        } catch (RuntimeException exception) {
            log.debug("AI Token 用量记录写入失败，model={}, purpose={}, reason={}",
                    modelName, purpose, exception.getMessage());
        }
    }

    /**
     * 按时间范围汇总 Token 消耗（按模型+用途分组）。
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return [{model_name, purpose, call_count, total_prompt_tokens, total_completion_tokens, total_estimated_cost}]
     */
    public List<Map<String, Object>> sumByModelAndPurpose(LocalDateTime startTime, LocalDateTime endTime) {
        return tokenUsageMapper.sumByModelAndPurpose(startTime, endTime);
    }

    /**
     * 统计最近 N 天的总费用。
     */
    public BigDecimal sumCostByDays(int days) {
        return tokenUsageMapper.sumCostByDays(days);
    }

    /**
     * 估算费用 = (总 Token 数 / 1,000,000) * 模型单价。
     */
    private BigDecimal estimateCost(String modelName, int promptTokens, int completionTokens) {
        BigDecimal price = MODEL_PRICE_PER_MILLION.getOrDefault(modelName, DEFAULT_PRICE);
        int totalTokens = promptTokens + completionTokens;
        return price.multiply(new BigDecimal(totalTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
    }
}
