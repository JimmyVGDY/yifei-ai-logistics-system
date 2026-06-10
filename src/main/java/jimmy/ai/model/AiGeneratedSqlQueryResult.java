package jimmy.ai.model;

import java.util.List;
import java.util.Map;

public record AiGeneratedSqlQueryResult(
        boolean executed,
        String message,
        List<Map<String, Object>> records) {

    public static AiGeneratedSqlQueryResult skipped() {
        return new AiGeneratedSqlQueryResult(false, "", List.of());
    }

    public static AiGeneratedSqlQueryResult message(String message) {
        return new AiGeneratedSqlQueryResult(true, message, List.of());
    }
}
