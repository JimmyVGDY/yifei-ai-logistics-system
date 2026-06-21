package jimmy.ai.service;

import jimmy.ai.entity.AiTokenUsage;
import jimmy.ai.mapper.AiTokenUsageMapper;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.util.ColumnExistenceChecker;
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
     * 模型单价（每百万 Token），输入和输出分开计费，按模型供应商使用对应币种。
     * <p>
     * DeepSeek 以人民币官方定价为准；OpenAI 以美元定价。
     * 旧模型名 deepseek-chat / deepseek-reasoner 将于 2026-07-24 废弃，保留兼容。
     * 未在列表中的模型按默认价格（USD）计费。
     */
    private static final Map<String, PricePair> MODEL_PRICES = Map.ofEntries(
            // ── OpenAI (美元, USD, 暂无缓存折扣) ──
            entry("gpt-4o-mini",  new PricePair("0.15", "0.15", "0.60", "USD")),
            entry("gpt-4o",       new PricePair("2.50", "2.50", "10.00", "USD")),
            entry("gpt-4-turbo",  new PricePair("10.00", "10.00", "30.00", "USD")),
            // ── DeepSeek (人民币, CNY) ──
            entry("deepseek-chat",       new PricePair("1.00", "0.02", "2.00", "CNY")),  // 即将废弃
            entry("deepseek-reasoner",    new PricePair("1.00", "0.02", "2.00", "CNY")), // 即将废弃
            entry("deepseek-v4-flash",    new PricePair("1.00", "0.02", "2.00", "CNY")),
            entry("deepseek-v4-pro",      new PricePair("3.13", "0.06", "6.26", "CNY"))
    );
    private static final PricePair DEFAULT_PRICES = new PricePair("0.15", "0.15", "0.60", "USD");

    /**
     * 输入/输出分别定价，支持缓存命中折扣。
     *
     * @param input        输入每百万 Token 价格（缓存未命中）
     * @param inputCached  输入每百万 Token 价格（缓存命中，约 98% 折扣）
     * @param output       输出每百万 Token 价格
     * @param currency     币种 (USD / CNY)
     */
    private record PricePair(BigDecimal input, BigDecimal inputCached, BigDecimal output, String currency) {
        PricePair(String input, String inputCached, String output, String currency) {
            this(new BigDecimal(input), new BigDecimal(inputCached), new BigDecimal(output), currency);
        }
    }

    private static Map.Entry<String, PricePair> entry(String model, PricePair pair) {
        return Map.entry(model, pair);
    }

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final AiTokenUsageMapper tokenUsageMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final ColumnExistenceChecker columnChecker;
    private volatile Boolean templateColumnsExist;

    public AiTokenUsageService(AiTokenUsageMapper tokenUsageMapper,
                                CompactSnowflakeIdGenerator idGenerator,
                                ColumnExistenceChecker columnChecker) {
        this.tokenUsageMapper = tokenUsageMapper;
        this.idGenerator = idGenerator;
        this.columnChecker = columnChecker;
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
                       int cachedTokens, String userId, String userCode, String conversationId,
                       String modelBaseUrl, long durationMs) {
        record(modelName, purpose, null, null, promptTokens, completionTokens, cachedTokens,
                userId, userCode, conversationId, modelBaseUrl, durationMs);
    }

    /**
     * 记录一次模型调用的 Token 消耗，并尽量附带 Prompt 模板版本。
     * <p>
     * 模板字段是增量字段，服务会先判断字段是否存在；旧库未迁移时自动回退到普通 Token 记录。
     */
    public void record(String modelName, String purpose, String templateCode, Integer templateVersion,
                       int promptTokens, int completionTokens, int cachedTokens,
                       String userId, String userCode,
                       String conversationId, String modelBaseUrl, long durationMs) {
        try {
            AiTokenUsage usage = new AiTokenUsage();
            usage.setId(idGenerator.nextId());
            usage.setModelName(modelName);
            usage.setPurpose(purpose);
            usage.setTemplateCode(templateCode);
            usage.setTemplateVersion(templateVersion);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setCachedTokens(cachedTokens);
            usage.setTotalTokens(promptTokens + completionTokens);
            usage.setUserId(userId);
            usage.setUserCode(userCode);
            usage.setConversationId(conversationId);
            PricePair prices = MODEL_PRICES.getOrDefault(modelName, DEFAULT_PRICES);
            usage.setEstimatedCost(estimateCost(prices, promptTokens, completionTokens, cachedTokens));
            usage.setEstimatedCostCurrency(prices.currency);
            usage.setModelBaseUrl(modelBaseUrl);
            usage.setDurationMs(durationMs);
            usage.setCreatedAt(LocalDateTime.now());
            if (hasTemplateColumns()) {
                tokenUsageMapper.insertWithTemplate(usage);
            } else {
                tokenUsageMapper.insert(usage);
            }
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
     * 估算费用 = (输入未命中 Token / 1M) × 输入单价 + (输入缓存命中 Token / 1M) × 缓存单价 + (输出 Token / 1M) × 输出单价。
     * <p>
     * 费用币种取决于模型供应商（DeepSeek → CNY, OpenAI → USD）。
     * 缓存命中的输入 Token（DeepSeek 约 98% 折扣：¥1.00 → ¥0.02 / M）。
     */
    private static BigDecimal estimateCost(PricePair prices, int promptTokens, int completionTokens, int cachedTokens) {
        int cacheMissTokens = promptTokens - cachedTokens;
        return prices.input.multiply(new BigDecimal(cacheMissTokens))
                .add(prices.inputCached.multiply(new BigDecimal(cachedTokens)))
                .add(prices.output.multiply(new BigDecimal(completionTokens)))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
    }

    private boolean hasTemplateColumns() {
        Boolean cached = templateColumnsExist;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (templateColumnsExist != null) {
                return templateColumnsExist;
            }
            templateColumnsExist = columnChecker.hasColumn("ai_token_usage", "template_code")
                    && columnChecker.hasColumn("ai_token_usage", "template_version");
            return templateColumnsExist;
        }
    }
}
