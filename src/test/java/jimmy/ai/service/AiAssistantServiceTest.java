package jimmy.ai.service;

import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiMessageVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiAssistantServiceTest {

    @Test
    void shouldSkipContinuationMessagesWhenResolvingPreviousUserQuery() {
        AiConversationVO conversation = new AiConversationVO(
                "c1",
                "订单查询",
                "2026-06-12 10:00:00",
                "2026-06-12 10:05:00",
                List.of(
                        new AiMessageVO("user", "我要看今天的订单的详细数据", "2026-06-12 10:00:00"),
                        new AiMessageVO("assistant", "已查询到今天订单 48 条，先展示前 20 条", "2026-06-12 10:01:00"),
                        new AiMessageVO("user", "查看剩余的28条", "2026-06-12 10:02:00"),
                        new AiMessageVO("assistant", "已继续展示剩余记录", "2026-06-12 10:03:00"),
                        new AiMessageVO("user", "下一批", "2026-06-12 10:04:00")
                )
        );

        assertThat(AiChatPipeline.latestUserMessage(conversation)).isEqualTo("我要看今天的订单的详细数据");
    }
}
