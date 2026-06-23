package jimmy.ai.service;

import jimmy.ai.mapper.AiQueryCursorMapper;
import jimmy.ai.model.AiQueryCursor;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * AI 查询游标服务。
 * <p>
 * 游标只保存只读查询的脱敏条件和分页位置，用于后续“继续看、剩余、下一页”这类追问。
 */
@Slf4j
@Service
public class AiQueryCursorService {

    private final AiQueryCursorMapper cursorMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final AiSensitiveDataMasker masker;
    private final long ttlMinutes;

    public AiQueryCursorService(AiQueryCursorMapper cursorMapper,
                                CompactSnowflakeIdGenerator idGenerator,
                                AiSensitiveDataMasker masker,
                                @Value("${app.ai.query-cursor.ttl-minutes:60}") long ttlMinutes) {
        this.cursorMapper = cursorMapper;
        this.idGenerator = idGenerator;
        this.masker = masker;
        this.ttlMinutes = Math.max(1, ttlMinutes);
    }

    public Optional<AiQueryCursor> latest(String conversationId, String userId, String userCode) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(cursorMapper.selectLatestActive(conversationId, userId, userCode));
        } catch (RuntimeException exception) {
            log.debug("读取 AI 查询游标失败，conversationId={}, reason={}", conversationId, exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<AiQueryCursor> findActive(String cursorId, String conversationId, String userId, String userCode) {
        if (!StringUtils.hasText(cursorId) || !StringUtils.hasText(conversationId) || !StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(cursorMapper.selectActiveByCursorId(cursorId, conversationId, userId, userCode));
        } catch (RuntimeException exception) {
            log.debug("按 ID 读取 AI 查询游标失败，cursorId={}, conversationId={}, reason={}",
                    cursorId, conversationId, exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<AiQueryCursor> create(String conversationId,
                                          String userId,
                                          String userCode,
                                          String toolType,
                                          String toolName,
                                          String moduleCode,
                                          String moduleName,
                                          String keyword,
                                          String startTime,
                                          String endTime,
                                          String statusFilter,
                                          int page,
                                          int pageSize,
                                          long total,
                                          int returnedCount,
                                          String querySummary) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(userId) || total <= returnedCount) {
            return Optional.empty();
        }
        try {
            AiQueryCursor cursor = new AiQueryCursor();
            cursor.setId(idGenerator.nextId());
            cursor.setCursorId(String.valueOf(idGenerator.nextId()));
            cursor.setConversationId(conversationId);
            cursor.setUserId(userId);
            cursor.setUserCode(userCode);
            cursor.setToolType(blankToDefault(toolType, "MODULE"));
            cursor.setToolName(masker.mask(toolName));
            cursor.setModuleCode(moduleCode);
            cursor.setModuleName(masker.mask(moduleName));
            cursor.setKeyword(masker.mask(keyword));
            cursor.setStartTime(startTime);
            cursor.setEndTime(endTime);
            cursor.setStatusFilter(statusFilter);
            cursor.setPage(Math.max(1, page));
            cursor.setPageSize(Math.max(1, pageSize));
            cursor.setTotal(total);
            cursor.setReturnedCount(Math.max(0, returnedCount));
            cursor.setQuerySummary(masker.mask(querySummary));
            cursor.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
            cursorMapper.insertCursor(cursor);
            return Optional.of(cursor);
        } catch (RuntimeException exception) {
            log.debug("写入 AI 查询游标失败，conversationId={}, reason={}", conversationId, exception.getMessage());
            return Optional.empty();
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 根据游标 ID 继续分页（供 Python Agent 的 continue_cursor 工具回调）。
     *
     * @param cursorId       游标 ID
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param userCode       用户编码
     * @param offset         偏移量（已返回条数）
     * @return 游标实体，不存在时返回 empty
     */
    public Optional<AiQueryCursor> continueCursor(String cursorId, String conversationId, String userId, String userCode, int offset) {
        if (!StringUtils.hasText(cursorId)) {
            return Optional.empty();
        }
        try {
            AiQueryCursor cursor = cursorMapper.selectActiveByCursorId(cursorId, conversationId, userId, userCode);
            if (cursor == null || cursor.getExpiresAt() == null) {
                return Optional.empty();
            }
            if (cursor.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.debug("游标已过期，cursorId={}, expiresAt={}", cursorId, cursor.getExpiresAt());
                return Optional.empty();
            }
            // 更新游标分页位置
            int pageSize = Math.max(1, cursor.getPageSize());
            int currentPage = offset / pageSize;
            cursor.setPage(Math.max(1, currentPage + 1));
            cursor.setReturnedCount(Math.max(0, offset));
            return Optional.of(cursor);
        } catch (RuntimeException exception) {
            log.debug("分页游标读取失败，cursorId={}, reason={}", cursorId, exception.getMessage());
            return Optional.empty();
        }
    }
}
