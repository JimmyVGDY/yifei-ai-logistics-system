package jimmy.ai.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiOutputValidatorTest {

    @Test
    void shouldExtractTextFromMarkdownFence() {
        AiOutputValidator validator = new AiOutputValidator();

        String text = validator.stripFence("""
                ```json
                {"ok":true}
                ```
                """);

        assertThat(text).isEqualTo("{\"ok\":true}");
    }

    @Test
    void shouldExtractJsonObjectFromNoisyOutput() {
        AiOutputValidator validator = new AiOutputValidator();

        Optional<String> json = validator.extractJsonObject("explain {\"hasMemory\":false} done");

        assertThat(json).contains("{\"hasMemory\":false}");
    }

    @Test
    void shouldRejectEmptyRequiredText() {
        AiOutputValidator validator = new AiOutputValidator();

        assertThatThrownBy(() -> validator.requireText(Optional.of(" "), "stage"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
    }

    @Test
    void shouldNormalizeSingleSelectOutput() {
        AiSqlOutputValidator validator = new AiSqlOutputValidator(new AiOutputValidator());

        String sql = validator.normalizeSelect("""
                ```sql
                select order_no from logistics_order limit 10;
                ```
                """);

        assertThat(sql).isEqualTo("select order_no from logistics_order limit 10");
    }

    @Test
    void shouldRejectDangerousSqlOutput() {
        AiSqlOutputValidator validator = new AiSqlOutputValidator(new AiOutputValidator());

        assertThatThrownBy(() -> validator.normalizeSelect("select id from logistics_order; drop table logistics_order"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQL_SECURITY_BLOCKED");
        assertThatThrownBy(() -> validator.normalizeSelect("update logistics_order set status='X'"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQL_SELF_CHECK_FAILED");
        assertThatThrownBy(() -> validator.normalizeSelect("select id from logistics_order -- comment"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQL_SECURITY_BLOCKED");
    }
}
