package jimmy.auth.service;

import jimmy.auth.model.LoginConflictResponse;
import jimmy.common.util.LogMaskUtils;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录冲突管理服务 —— 同一账号多地登录时的新旧会话协调逻辑。
 * <p>
 * 设计目标：避免同一账号多个会话同时在线导致操作混乱，但给原会话一个"接受"或"拒绝"的缓冲期。
 * <p>
 * 流程：
 * <ol>
 *   <li>新登录 → 检测到已有会话 → 创建 PENDING 冲突，返回 conflictId</li>
 *   <li>新页面轮询 {@link AuthService#loginConflictStatus}（每 2 秒）</li>
 *   <li>原会话选择 接受 → ACCEPTED → 新页面自动完成登录</li>
 *   <li>原会话选择 拒绝 → REJECTED → 新页面显示拒绝提示</li>
 *   <li>60 秒无响应 → EXPIRED → 新登录自动生效（TAKEN_OVER）</li>
 * </ol>
 * <p>
 * 全部状态存在于内存 {@link ConcurrentHashMap}，服务重启后丢失。
 */
@Service
public class LoginConflictService {

    /** 等待原会话确认的超时时间（毫秒），超时后新登录自动生效 */
    private static final long CONFIRM_WINDOW_MILLIS = 60_000L;

    private final Map<String, PendingLoginConflict> conflicts = new ConcurrentHashMap<>();
    private final Map<Long, String> latestConflictByUser = new ConcurrentHashMap<>();

    /**
     * 创建登录冲突。同一用户已有 PENDING 冲突时，旧的自动标记为 REJECTED。
     *
     * @param userId 账号用户 ID
     * @param username 登录账号（仅用于 masked 展示，不存明文）
     * @return 含 conflictId / 倒计时秒数的冲突响应
     */
    public LoginConflictResponse create(Long userId, String username) {
        cleanupExpired();
        String oldConflictId = latestConflictByUser.get(userId);
        if (oldConflictId != null) {
            PendingLoginConflict old = conflicts.get(oldConflictId);
            if (old != null && "PENDING".equals(old.status)) {
                old.status = "REJECTED";
                old.message = "已有新的登录确认请求，本次请求已失效";
            }
        }
        String conflictId = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        PendingLoginConflict conflict = new PendingLoginConflict();
        conflict.conflictId = conflictId;
        conflict.userId = userId;
        conflict.usernameMasked = LogMaskUtils.maskAccount(username);
        conflict.expireAt = now + CONFIRM_WINDOW_MILLIS;
        conflict.status = "PENDING";
        conflict.message = "该账号已在其他地方登录，正在等待原会话确认";
        conflicts.put(conflictId, conflict);
        latestConflictByUser.put(userId, conflictId);
        return toResponse(conflict);
    }

    /**
     * 查询当前用户的 PENDING 冲突（原会话页面轮询用）。
     * 若无活跃冲突返回 null。
     */
    public LoginConflictResponse current(Long userId) {
        cleanupExpired();
        String conflictId = latestConflictByUser.get(userId);
        if (conflictId == null) {
            return null;
        }
        PendingLoginConflict conflict = conflicts.get(conflictId);
        if (conflict == null || !"PENDING".equals(conflict.status)) {
            return null;
        }
        return toResponse(conflict);
    }

    /** 根据 conflictId 查询冲突状态，不存在时返回 REJECTED */
    public LoginConflictResponse status(String conflictId) {
        cleanupExpired();
        PendingLoginConflict conflict = conflicts.get(conflictId);
        return conflict == null ? rejected("登录确认请求不存在或已过期") : toResponse(conflict);
    }

    /**
     * 获取活跃状态的冲突对象（PENDING 或 ACCEPTED）。
     * <p>
     * ACCEPTED 保留在活跃集合中，供新页面轮询检测并完成登录。
     */
    public PendingLoginConflict pending(String conflictId) {
        cleanupExpired();
        PendingLoginConflict conflict = conflicts.get(conflictId);
        if (conflict == null) {
            return null;
        }
        // PENDING（等待确认）和 ACCEPTED（已接受，待轮询完成登录）都视为活跃状态
        return ("PENDING".equals(conflict.status) || "ACCEPTED".equals(conflict.status)) ? conflict : null;
    }

    /** 判断冲突是否已到达过期时间 */
    public boolean isExpired(PendingLoginConflict conflict) {
        return conflict != null && System.currentTimeMillis() >= conflict.expireAt;
    }

    /**
     * 拒绝新的登录请求。
     * 将状态设置为 REJECTED 并从 latestConflictByUser 中移除映射。
     */
    public LoginConflictResponse reject(String conflictId, Long userId) {
        PendingLoginConflict conflict = conflicts.get(conflictId);
        if (conflict == null || !userId.equals(conflict.userId)) {
            return rejected("登录确认请求不存在或无权处理");
        }
        conflict.status = "REJECTED";
        conflict.message = "原会话已拒绝新的登录请求";
        latestConflictByUser.remove(userId, conflictId);
        return toResponse(conflict);
    }

    /**
     * 标记冲突为 TAKEN_OVER（超时后新登录自动生效）。
     * 清理 latestConflictByUser 映射，避免残留。
     */
    public void markTakenOver(String conflictId) {
        PendingLoginConflict conflict = conflicts.get(conflictId);
        if (conflict == null) {
            return;
        }
        conflict.status = "TAKEN_OVER";
        conflict.message = "原会话未在一分钟内拒绝，新登录已生效";
        latestConflictByUser.remove(conflict.userId, conflictId);
    }

    /** 原会话主动接受：标记冲突为已接受，新页面轮询检测后完成登录 */
    public void accept(String conflictId) {
        PendingLoginConflict conflict = conflicts.get(conflictId);
        if (conflict == null) {
            return;
        }
        conflict.status = "ACCEPTED";
        conflict.message = "原会话已允许新的登录请求";
    }

    /**
     * 清理过期冲突。
     * <p>
     * 每次操作前调用，确保 PENDING 超时的标记为 EXPIRED、
     * 已完成/过期超过一个窗口期的从内存中彻底移除。
     */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingLoginConflict>> iterator = conflicts.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingLoginConflict conflict = iterator.next().getValue();
            // 已完成或已过期的冲突均需清理，包括用户未响应导致的过期 PENDING。
            if ("PENDING".equals(conflict.status) && now >= conflict.expireAt) {
                conflict.status = "EXPIRED";
                conflict.message = "登录确认请求已过期";
            }
            if (!"PENDING".equals(conflict.status) && now - conflict.expireAt > CONFIRM_WINDOW_MILLIS) {
                latestConflictByUser.remove(conflict.userId, conflict.conflictId);
                iterator.remove();
            }
        }
    }

    private LoginConflictResponse rejected(String message) {
        LoginConflictResponse response = new LoginConflictResponse();
        response.setLoginStatus("REJECTED");
        response.setMessage(message);
        return response;
    }

    private LoginConflictResponse toResponse(PendingLoginConflict conflict) {
        LoginConflictResponse response = new LoginConflictResponse();
        response.setLoginStatus(conflict.status);
        response.setConflictId(conflict.conflictId);
        response.setUsernameMasked(conflict.usernameMasked);
        response.setExpireAt(conflict.expireAt);
        response.setRemainingSeconds(Math.max(0, (conflict.expireAt - System.currentTimeMillis() + 999) / 1000));
        response.setMessage(conflict.message);
        return response;
    }

    public static class PendingLoginConflict {
        private String conflictId;
        private Long userId;
        private String usernameMasked;
        private long expireAt;
        private String status;
        private String message;

        public String getConflictId() { return conflictId; }
        public Long getUserId() { return userId; }
        public String getStatus() { return status; }
    }
}
