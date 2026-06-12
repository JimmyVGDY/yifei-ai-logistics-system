package jimmy.ai.service;

import jimmy.ai.mapper.AiMessageFeedbackMapper;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiMessageVO;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.common.trace.TraceContextSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiAssistantServiceTest {

    @Test
    void shouldSkipContinuationMessagesWhenResolvingPreviousUserQuery() {
        AiAssistantService service = new AiAssistantService(
                mock(AiKnowledgeService.class),
                mock(AiReadonlyQueryService.class),
                mock(AiLogAnalysisService.class),
                mock(AiModelGateway.class),
                mock(AiConversationService.class),
                mock(AiSensitiveDataMasker.class),
                mock(TraceContextSupport.class),
                mock(AiAuditLogService.class),
                mock(AiBusinessQueryTools.class),
                mock(AiToolCallContext.class),
                mock(AiMemoryService.class),
                mock(AiFallbackHandler.class),
                mock(AiMessageFeedbackMapper.class),
                mock(CompactSnowflakeIdGenerator.class)
        );
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

        assertThat(service.latestUserMessage(conversation)).isEqualTo("我要看今天的订单的详细数据");
    }
}
