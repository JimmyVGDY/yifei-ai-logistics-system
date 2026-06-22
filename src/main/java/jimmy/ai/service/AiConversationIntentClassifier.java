package jimmy.ai.service;

import jimmy.ai.model.AiConversationIntent;
import jimmy.ai.model.AiExecutionMode;
import jimmy.ai.model.AiExecutionPlan;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 对话意图分类器。
 * <p>
 * 这层位于模型工具调用之前，专门区分“用户在聊天/纠偏/限定范围”和“用户真的要查业务数据”。
 * 它的职责是收窄查询触发边界，避免模型一看到业务关键词就擅自调用只读查询工具。
 */
@Service
public class AiConversationIntentClassifier {

    private final AiIntentPlanner intentPlanner;

    public AiConversationIntentClassifier(AiIntentPlanner intentPlanner) {
        this.intentPlanner = intentPlanner;
    }

    public AiConversationIntent classify(String message, String previousUserMessage) {
        String text = normalize(message);
        if (!StringUtils.hasText(text)) {
            return AiConversationIntent.fromPlan(AiExecutionPlan.general("空问题，走普通问答兜底"));
        }
        if (isControlPreference(text)) {
            return AiConversationIntent.direct(AiExecutionMode.CONTROL_PREFERENCE,
                    controlPreferenceAnswer(text), "用户正在纠正 AI 行为或限定后续查询范围");
        }
        if (isGeneralChat(text)) {
            return AiConversationIntent.fromPlan(AiExecutionPlan.general("用户在讨论或追问 AI 行为，不触发业务查询"));
        }
        if (isContinuation(text, previousUserMessage)) {
            AiExecutionPlan basePlan = intentPlanner.plan(text);
            return AiConversationIntent.fromPlan(new AiExecutionPlan(AiExecutionMode.QUERY_CONTINUATION,
                    basePlan.candidateModules(), basePlan.keyword(), "命中上下文续查表达，优先继承上一轮查询状态"));
        }
        if (isClarifyRequired(text, previousUserMessage)) {
            return AiConversationIntent.direct(AiExecutionMode.CLARIFY_REQUIRED,
                    clarifyAnswer(), "业务对象存在歧义且缺少可继承上下文");
        }
        return AiConversationIntent.fromPlan(intentPlanner.plan(text));
    }

    private boolean isControlPreference(String text) {
        boolean controlWord = containsAny(text, "记住", "记住了吗", "以后", "下次", "不要", "别再", "不要再",
                "只查", "只看", "我说的是", "我的意思", "不是让你", "你理解错", "你听懂", "听懂了吗",
                "明白了吗", "懂了吗", "不要同时", "别同时", "范围限定", "限定在");
        if (!controlWord) {
            return false;
        }
        /*
         * “查一下只看待处理异常”这类句子虽然包含“只看”，但主语义仍是查询。
         * 只有出现明显纠偏、未来偏好或确认理解时，才拦截为控制意图。
         */
        boolean explicitCorrection = containsAny(text, "记住", "以后", "下次", "我说的是", "我的意思", "不是让你",
                "你理解错", "不要同时", "别同时", "记住了吗", "明白了吗", "懂了吗", "听懂了吗");
        return explicitCorrection || !hasQueryAction(text);
    }

    private boolean isGeneralChat(String text) {
        return containsAny(text, "为什么你", "你刚刚", "你查错", "怎么理解", "怎么办", "怎么设计",
                "你是谁", "能做什么", "怎么用", "说明一下", "解释一下");
    }

    private boolean isClarifyRequired(String text, String previousUserMessage) {
        if (StringUtils.hasText(previousUserMessage)) {
            return false;
        }
        return List.of("异常任务", "任务异常", "异常订单", "异常运单")
                .contains(text);
    }

    private boolean isContinuation(String text, String previousUserMessage) {
        return StringUtils.hasText(previousUserMessage)
                && containsAny(text, "只要", "只看", "剩下", "剩余", "继续", "下一页", "待处理", "运输中", "已完成");
    }

    private String controlPreferenceAnswer(String text) {
        String scope = text.contains("运输任务") ? "运输任务模块"
                : text.contains("异常管理") ? "异常管理模块"
                : text.contains("订单") ? "订单管理模块"
                : text.contains("运单") ? "运单中心"
                : "你刚刚限定的范围";
        return "明白了，后续我会优先按“" + scope + "”理解你的查询范围。"
                + "当你是在纠正我、要求记住范围或确认我是否理解时，我不会马上查库；"
                + "等你说“现在查一下”或补充具体查询条件时，我再按这个范围执行只读查询。";
    }

    private String clarifyAnswer() {
        return "这个说法有点模糊。你是想查运输任务中的异常任务、异常管理中的异常记录，"
                + "还是订单/运单的异常状态？请补充一个范围，我再帮你查询。";
    }

    private boolean hasQueryAction(String text) {
        return containsAny(text, "查", "查询", "搜索", "看看", "看一下", "统计", "汇总", "分析", "列出", "给我看");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\u3000', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", "")
                .trim();
    }
}
