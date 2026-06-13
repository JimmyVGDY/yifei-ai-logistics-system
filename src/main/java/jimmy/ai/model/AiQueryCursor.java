package jimmy.ai.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 查询游标。
 * <p>
 * 用于把“上一轮查了什么、已展示多少、下一页从哪里开始”固化下来，
 * 避免多轮追问时重新猜测用户上下文。
 */
@Data
public class AiQueryCursor {
    private Long id;
    private String cursorId;
    private String conversationId;
    private String userId;
    private String userCode;
    private String toolType;
    private String toolName;
    private String moduleCode;
    private String moduleName;
    private String keyword;
    private String startTime;
    private String endTime;
    private String statusFilter;
    private Integer page;
    private Integer pageSize;
    private Long total;
    private Integer returnedCount;
    private String querySummary;
    private LocalDateTime expiresAt;
}
