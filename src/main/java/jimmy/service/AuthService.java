package jimmy.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import jimmy.config.AdminAccountProperties;
import jimmy.model.LoginRequest;
import jimmy.model.LoginResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final String ADMIN_LOGIN_ID = "admin";

    private final AdminAccountProperties adminAccountProperties;

    public AuthService(AdminAccountProperties adminAccountProperties) {
        this.adminAccountProperties = adminAccountProperties;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request == null ? null : request.getUsername();
        String password = request == null ? null : request.getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("账号和密码不能为空");
        }
        if (!adminAccountProperties.getUsername().equals(username)
                || !adminAccountProperties.getPassword().equals(password)) {
            throw new IllegalArgumentException("管理员账号或密码错误");
        }

        StpUtil.login(ADMIN_LOGIN_ID);
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginResponse(username, StpUtil.getLoginId(), tokenInfo.getTokenName(), tokenInfo.getTokenValue());
    }

    public LoginResponse currentSession() {
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginResponse(adminAccountProperties.getUsername(), StpUtil.getLoginId(), tokenInfo.getTokenName(), tokenInfo.getTokenValue());
    }

    public void logout() {
        StpUtil.logout();
    }
}
