package jimmy.ai.service;

import jimmy.ai.model.AiConversationIntent;
import jimmy.ai.model.AiExecutionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiConversationIntentClassifierTest {

    private final AiConversationIntentClassifier classifier =
            new AiConversationIntentClassifier(new AiIntentPlanner());

    @Test
    void shouldTreatCorrectionAsPreferenceWithoutQueryTools() {
        AiConversationIntent intent = classifier.classify(
                "我说运输任务里的异常的任务，你不要同时给我查其他模块的异常，就查运输任务中的就行了，记住了吗？",
                "查一下异常任务"
        );

        assertThat(intent.executionPlan().mode()).isEqualTo(AiExecutionMode.CONTROL_PREFERENCE);
        assertThat(intent.directAnswer()).isTrue();
        assertThat(intent.allowBusinessTools()).isFalse();
        assertThat(intent.allowReadonlyFallback()).isFalse();
        assertThat(intent.answer()).contains("运输任务模块");
    }

    @Test
    void shouldQueryOnlyTaskModuleWhenUserClearlyLimitsExceptionTask() {
        AiConversationIntent intent = classifier.classify("查一下运输任务里的异常任务", null);

        assertThat(intent.executionPlan().mode()).isEqualTo(AiExecutionMode.MODULE_QUERY);
        assertThat(intent.executionPlan().candidateModules()).containsExactly("运输任务");
        assertThat(intent.allowBusinessTools()).isTrue();
        assertThat(intent.allowReadonlyFallback()).isTrue();
    }

    @Test
    void shouldAskClarificationForAmbiguousExceptionTaskWithoutContext() {
        AiConversationIntent intent = classifier.classify("异常任务", null);

        assertThat(intent.executionPlan().mode()).isEqualTo(AiExecutionMode.CLARIFY_REQUIRED);
        assertThat(intent.directAnswer()).isTrue();
        assertThat(intent.allowBusinessTools()).isFalse();
        assertThat(intent.answer()).contains("运输任务中的异常任务", "异常管理中的异常记录");
    }

    @Test
    void shouldUseGlobalSearchForShortKeywordWithoutContext() {
        AiConversationIntent intent = classifier.classify("陈土豆", null);

        assertThat(intent.executionPlan().mode()).isEqualTo(AiExecutionMode.GLOBAL_SEARCH);
        assertThat(intent.allowBusinessTools()).isTrue();
    }

    @Test
    void shouldTreatAiBehaviorQuestionAsGeneralChat() {
        AiConversationIntent intent = classifier.classify("为什么你刚刚查错了", "查异常管理");

        assertThat(intent.executionPlan().mode()).isEqualTo(AiExecutionMode.GENERAL_CHAT);
        assertThat(intent.allowBusinessTools()).isFalse();
        assertThat(intent.allowReadonlyFallback()).isFalse();
    }

    @Test
    void shouldMarkFollowUpAsQueryContinuation() {
        AiConversationIntent intent = classifier.classify("只要待处理的", "查异常管理");

        assertThat(intent.executionPlan().mode()).isEqualTo(AiExecutionMode.QUERY_CONTINUATION);
        assertThat(intent.allowBusinessTools()).isTrue();
        assertThat(intent.allowReadonlyFallback()).isTrue();
    }
}
