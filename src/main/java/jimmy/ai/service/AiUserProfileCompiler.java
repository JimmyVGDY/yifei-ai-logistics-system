package jimmy.ai.service;

import jimmy.ai.model.AiMemoryItemVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 根据生效长期记忆编译账号画像。
 * <p>
 * 画像只是“可重建缓存”，真正可审计的事实仍在 ai_user_memory。
 */
@Service
public class AiUserProfileCompiler {

    public ProfileSnapshot compile(List<AiMemoryItemVO> activeMemories, String fallbackAnswerStyle) {
        List<AiMemoryItemVO> memories = activeMemories == null ? List.of() : activeMemories;
        String answerStyle = memories.stream()
                .filter(memory -> "ANSWER_STYLE".equals(memory.memoryType()))
                .sorted((a, b) -> comparePriority(b, a))
                .map(AiMemoryItemVO::memorySummary)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(fallbackAnswerStyle);

        String favoriteModules = memories.stream()
                .filter(memory -> "FAVORITE_MODULE".equals(memory.memoryType()))
                .sorted((a, b) -> comparePriority(b, a))
                .map(AiMemoryItemVO::memoryTitle)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(20)
                .collect(Collectors.joining("\n"));

        String queryHabits = memories.stream()
                .filter(memory -> "QUERY_HABIT".equals(memory.memoryType()))
                .sorted((a, b) -> comparePriority(b, a))
                .map(AiMemoryItemVO::memorySummary)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(20)
                .collect(Collectors.joining("\n"));

        Map<String, Long> moduleWeights = memories.stream()
                .filter(memory -> StringUtils.hasText(memory.scopeValue()))
                .collect(Collectors.groupingBy(AiMemoryItemVO::scopeValue, LinkedHashMap::new, Collectors.counting()));

        double confidence = memories.stream()
                .map(AiMemoryItemVO::confidence)
                .filter(value -> value != null && value > 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.80);

        String answerStyleJson = "{\"style\":\"" + escape(answerStyle) + "\"}";
        String queryStrategyJson = "{\"habits\":\"" + escape(queryHabits) + "\",\"scopePolicy\":\"explicit_and_current_scope_first\"}";
        String moduleAffinityJson = moduleWeights.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));

        return new ProfileSnapshot(answerStyle, favoriteModules, queryHabits,
                answerStyleJson, queryStrategyJson, moduleAffinityJson, Math.min(confidence, 0.98));
    }

    private int comparePriority(AiMemoryItemVO left, AiMemoryItemVO right) {
        int priorityCompare = Integer.compare(nullToZero(left.priority()), nullToZero(right.priority()));
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return Double.compare(nullToZero(left.confidence()), nullToZero(right.confidence()));
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private double nullToZero(Double value) {
        return value == null ? 0 : value;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record ProfileSnapshot(
            String answerStyle,
            String favoriteModules,
            String queryHabits,
            String answerStyleJson,
            String queryStrategyJson,
            String moduleAffinityJson,
            double profileConfidence) {
    }
}
