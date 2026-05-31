package jimmy.controller;

import jimmy.logistics.annotation.OperationLog;
import jimmy.model.ApiResponse;
import jimmy.model.LoginRequest;
import jimmy.model.LoginResponse;
import jimmy.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
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
}
