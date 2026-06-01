package jimmy.controller;

import jimmy.logistics.annotation.OperationLog;
import jimmy.model.ApiResponse;
import jimmy.model.LoginConflictResponse;
import jimmy.model.LoginRequest;
import jimmy.model.LoginResponse;
import jimmy.model.PasswordChangeRequest;
import jimmy.model.ProfileUpdateRequest;
import jimmy.service.AuthService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 认证控制器 —— 登录、会话恢复、退出。
 * <p>
 * 登录成功后返回 Sa-Token 令牌和用户权限/菜单信息，
 * 前端存储 token 并在后续请求中通过请求头携带。
 * </p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 用户登录 —— 返回 token、权限码列表和菜单树 */
    @OperationLog("用户登录")
    @PostMapping("/login")
    public ApiResponse<Object> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/login-conflicts/{conflictId}/status")
    public ApiResponse<LoginConflictResponse> loginConflictStatus(@PathVariable String conflictId) {
        return ApiResponse.success(authService.loginConflictStatus(conflictId));
    }

    @GetMapping("/login-conflicts/current")
    public ApiResponse<LoginConflictResponse> currentLoginConflict() {
        return ApiResponse.success(authService.currentLoginConflict());
    }

    @PostMapping("/login-conflicts/{conflictId}/reject")
    @OperationLog("拒绝登录冲突")
    public ApiResponse<LoginConflictResponse> rejectLoginConflict(@PathVariable String conflictId) {
        return ApiResponse.success(authService.rejectLoginConflict(conflictId));
    }

    @PostMapping("/login-conflicts/{conflictId}/accept")
    @OperationLog("允许登录冲突")
    public ApiResponse<LoginConflictResponse> acceptLoginConflict(@PathVariable String conflictId) {
        return ApiResponse.success(authService.acceptLoginConflict(conflictId));
    }

    /** 会话恢复 —— 用于刷新页面后从 localStorage 恢复登录态 */
    @GetMapping("/session")
    public ApiResponse<LoginResponse> session() {
        return ApiResponse.success(authService.currentSession());
    }

    /** 用户退出 —— 清空服务端会话 */
    @OperationLog("用户退出")
    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        authService.logout();
        return ApiResponse.success(Boolean.TRUE);
    }

    /** 修改个人资料 */
    @OperationLog("修改个人资料")
    @PutMapping("/profile")
    public ApiResponse<Boolean> updateProfile(@RequestBody ProfileUpdateRequest request) {
        authService.updateProfile(request);
        return ApiResponse.success(Boolean.TRUE);
    }

    /** 修改密码 */
    @OperationLog("修改密码")
    @PutMapping("/password")
    public ApiResponse<Boolean> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(request);
        return ApiResponse.success(Boolean.TRUE);
    }
}
