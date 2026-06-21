package jimmy.ai.service;

import jimmy.ai.entity.AiPromptTemplate;
import jimmy.ai.mapper.AiPromptTemplateMapper;
import jimmy.ai.model.AiPromptTemplateCodes;
import jimmy.ai.model.PromptRenderResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiPromptTemplateServiceTest {

    @Test
    void shouldOnlyRenderDeclaredVariables() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        AiPromptTemplate template = template("CUSTOM_TEMPLATE", "{{name}} {{secret}} {{optional}}", "name", "optional");
        when(mapper.findLatestActive("CUSTOM_TEMPLATE")).thenReturn(template);
        AiPromptTemplateService service = new AiPromptTemplateService(mapper, new DefaultAiPromptTemplates(), true, true);

        PromptRenderResult result = service.render("CUSTOM_TEMPLATE", Map.of(
                "name", "Alice",
                "optional", "visible",
                "secret", "should-not-render"
        ));

        assertThat(result.content()).contains("Alice").contains("visible").doesNotContain("should-not-render");
        assertThat(result.fallback()).isFalse();
    }

    @Test
    void shouldFallbackWhenDatabaseTemplateReadFails() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        when(mapper.findLatestActive(AiPromptTemplateCodes.AI_CHAT_SYSTEM))
                .thenThrow(new RuntimeException("table missing"));
        AiPromptTemplateService service = new AiPromptTemplateService(mapper, new DefaultAiPromptTemplates(), true, true);

        PromptRenderResult result = service.render(AiPromptTemplateCodes.AI_CHAT_SYSTEM, Map.of("toolMaxCalls", 8));

        assertThat(result.templateCode()).isEqualTo(AiPromptTemplateCodes.AI_CHAT_SYSTEM);
        assertThat(result.templateVersion()).isZero();
        assertThat(result.fallback()).isTrue();
        assertThat(result.content()).contains("8");
    }

    @Test
    void shouldFallbackWhenDatabaseTemplateMissesRequiredVariables() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        AiPromptTemplate template = template(AiPromptTemplateCodes.AI_CHAT_SYSTEM, "db-template {{missing}}", "missing", "");
        when(mapper.findLatestActive(AiPromptTemplateCodes.AI_CHAT_SYSTEM)).thenReturn(template);
        AiPromptTemplateService service = new AiPromptTemplateService(mapper, new DefaultAiPromptTemplates(), true, true);

        PromptRenderResult result = service.render(AiPromptTemplateCodes.AI_CHAT_SYSTEM, Map.of("toolMaxCalls", 8));

        assertThat(result.fallback()).isTrue();
        assertThat(result.content()).doesNotContain("db-template");
    }

    private AiPromptTemplate template(String code, String content, String required, String optional) {
        AiPromptTemplate template = new AiPromptTemplate();
        template.setTemplateCode(code);
        template.setTemplateVersion(3);
        template.setTemplateContent(content);
        template.setRequiredVariables(required);
        template.setOptionalVariables(optional);
        template.setModelPurpose("test");
        return template;
    }
}
