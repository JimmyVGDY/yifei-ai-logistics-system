package jimmy.service;

import jimmy.model.LoginConflictResponse;
import jimmy.util.LogMaskUtils;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginConflictService {

    private static final long CONFIRM_WINDOW_MILLIS = 60_000L;

    private final Map<String, PendingLoginConflict> conflicts = new ConcurrentHashMap<>();
    private final Map<Long, String> latestConflictByUser = new ConcurrentHashMap<>();

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

    public LoginConflictResponse status(String conflictId) {
        cleanupExpired();
        PendingLoginConflict conflict = conflicts.get(conflictId);
        return conflict == null ? rejected("登录确认请求不存在或已过期") : toResponse(conflict);
    }

    public PendingLoginConflict pending(String conflictId) {
        cleanupExpired();
        PendingLoginConflict conflict = conflicts.get(conflictId);
        return conflict != null && "PENDING".equals(conflict.status) ? conflict : null;
    }

    public boolean isExpired(PendingLoginConflict conflict) {
        return conflict != null && System.currentTimeMillis() >= conflict.expireAt;
    }

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

    public void markTakenOver(String conflictId) {
        PendingLoginConflict conflict = conflicts.get(conflictId);
        if (conflict == null) {
            return;
        }
        conflict.status = "TAKEN_OVER";
        conflict.message = "原会话未在一分钟内拒绝，新登录已生效";
        latestConflictByUser.remove(conflict.userId, conflictId);
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingLoginConflict>> iterator = conflicts.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingLoginConflict conflict = iterator.next().getValue();
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
    }
}
