package jimmy.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import jimmy.model.LoginRequest;
import jimmy.model.LoginResponse;
import jimmy.model.MenuVO;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request == null ? null : request.getUsername();
        String password = request == null ? null : request.getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("登录失败，账号或密码为空");
            throw new IllegalArgumentException("账号和密码不能为空");
        }

        LoginUser loginUser = findLoginUser(username);
        if (loginUser == null || !password.equals(loginUser.password)) {
            log.warn("登录失败，账号或密码错误，username={}", LogMaskUtils.maskAccount(username));
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (loginUser.status == null || loginUser.status != 1) {
            throw new IllegalArgumentException("账号已停用");
        }

        List<MenuVO> menus = queryMenus(loginUser.roleId);
        List<String> permissions = menus.stream()
                .map(MenuVO::getPermissionCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        StpUtil.login(loginUser.id);
        StpUtil.getSession().set("username", loginUser.username);
        StpUtil.getSession().set("usernameMasked", LogMaskUtils.maskAccount(loginUser.username));
        StpUtil.getSession().set("realNameMasked", LogMaskUtils.maskName(loginUser.realName));
        StpUtil.getSession().set("roleCode", loginUser.roleCode);
        StpUtil.getSession().set("roleName", loginUser.roleName);
        StpUtil.getSession().set("permissions", permissions);
        StpUtil.getSession().set("menus", menus);

        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        log.info("账号登录成功，userId={}, username={}, roleCode={}",
                loginUser.id, LogMaskUtils.maskAccount(loginUser.username), loginUser.roleCode);
        return buildResponse(loginUser, tokenInfo, permissions, menus);
    }

    public LoginResponse currentSession() {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        LoginUser loginUser = findLoginUserById(userId);
        List<MenuVO> menus = getSessionList("menus");
        List<String> permissions = getSessionList("permissions");
        if (menus.isEmpty() && loginUser != null) {
            menus = queryMenus(loginUser.roleId);
        }
        if (permissions.isEmpty()) {
            permissions = menus.stream().map(MenuVO::getPermissionCode).filter(StringUtils::hasText).collect(Collectors.toList());
        }
        return buildResponse(loginUser, StpUtil.getTokenInfo(), permissions, menus);
    }

    public void logout() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        String usernameMasked = loginId == null ? "" : String.valueOf(StpUtil.getSession().get("usernameMasked", ""));
        log.info("账号退出登录，userId={}, username={}", loginId, usernameMasked);
        StpUtil.logout();
    }

    private LoginResponse buildResponse(LoginUser loginUser, SaTokenInfo tokenInfo,
                                        List<String> permissions, List<MenuVO> menus) {
        if (loginUser == null) {
            throw new IllegalArgumentException("登录用户不存在");
        }
        return new LoginResponse(
                loginUser.username,
                loginUser.id,
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                loginUser.id,
                LogMaskUtils.maskAccount(loginUser.username),
                LogMaskUtils.maskName(loginUser.realName),
                loginUser.roleCode,
                loginUser.roleName,
                permissions,
                menus
        );
    }

    private LoginUser findLoginUser(String username) {
        List<LoginUser> users = jdbcTemplate.query(
                "select u.id, u.username, u.real_name, u.password, u.status, r.id role_id, r.role_code, r.role_name " +
                        "from sys_user u left join sys_role r on r.id = u.role_id where u.username = ?",
                (rs, rowNum) -> mapUser(rs.getLong("id"), rs.getString("username"), rs.getString("real_name"),
                        rs.getString("password"), rs.getInt("status"), rs.getLong("role_id"),
                        rs.getString("role_code"), rs.getString("role_name")),
                username
        );
        return users.isEmpty() ? null : users.get(0);
    }

    private LoginUser findLoginUserById(Long userId) {
        List<LoginUser> users = jdbcTemplate.query(
                "select u.id, u.username, u.real_name, u.password, u.status, r.id role_id, r.role_code, r.role_name " +
                        "from sys_user u left join sys_role r on r.id = u.role_id where u.id = ?",
                (rs, rowNum) -> mapUser(rs.getLong("id"), rs.getString("username"), rs.getString("real_name"),
                        rs.getString("password"), rs.getInt("status"), rs.getLong("role_id"),
                        rs.getString("role_code"), rs.getString("role_name")),
                userId
        );
        return users.isEmpty() ? null : users.get(0);
    }

    private LoginUser mapUser(Long id, String username, String realName, String password, Integer status,
                              Long roleId, String roleCode, String roleName) {
        LoginUser user = new LoginUser();
        user.id = id;
        user.username = username;
        user.realName = realName;
        user.password = password;
        user.status = status;
        user.roleId = roleId;
        user.roleCode = roleCode;
        user.roleName = roleName;
        return user;
    }

    private List<MenuVO> queryMenus(Long roleId) {
        List<MenuVO> rows = jdbcTemplate.query(
                "select m.id, m.parent_id, m.menu_name, m.menu_path, m.permission_code, m.sort_no " +
                        "from sys_menu m join sys_role_menu rm on rm.menu_id = m.id " +
                        "where rm.role_id = ? and m.status = 1 order by m.sort_no, m.id",
                (rs, rowNum) -> {
                    MenuVO menu = new MenuVO();
                    menu.setId(rs.getLong("id"));
                    menu.setParentId(rs.getLong("parent_id"));
                    menu.setName(rs.getString("menu_name"));
                    menu.setPath(rs.getString("menu_path"));
                    menu.setPermissionCode(rs.getString("permission_code"));
                    menu.setSortNo(rs.getInt("sort_no"));
                    return menu;
                },
                roleId
        );
        Map<Long, MenuVO> byId = new LinkedHashMap<>();
        rows.forEach(menu -> byId.put(menu.getId(), menu));
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO menu : rows) {
            if (menu.getParentId() != null && menu.getParentId() != 0 && byId.containsKey(menu.getParentId())) {
                byId.get(menu.getParentId()).getChildren().add(menu);
            } else {
                roots.add(menu);
            }
        }
        return roots;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getSessionList(String key) {
        Object value = StpUtil.getSession().get(key);
        return value instanceof List ? (List<T>) value : new ArrayList<>();
    }

    private static class LoginUser {
        private Long id;
        private String username;
        private String realName;
        private String password;
        private Integer status;
        private Long roleId;
        private String roleCode;
        private String roleName;
    }
}
