package jimmy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录成功响应 —— 包含 Sa-Token 令牌、用户基本信息、权限码列表、菜单树。
 */
public class LoginResponse {

    /** 用户名 */
    private String username;
    /** Sa-Token 登录ID */
    private Object loginId;
    /** Token 名称（Sa-Token tokenName） */
    private String tokenName;
    /** Token 值 */
    private String tokenValue;
    /** 登录会话审计ID，用于串联一次登录期间的所有操作 */
    private String loginSessionId;
    /** 用户ID */
    private Long userId;
    /** 用户编号 */
    private String userCode;
    /** 脱敏后的用户名 */
    private String usernameMasked;
    /** 脱敏后的真实姓名 */
    private String realNameMasked;
    /** 角色编码 */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
    /** 权限码列表 */
    private List<String> permissions = new ArrayList<>();
    /** 菜单树 */
    private List<MenuVO> menus = new ArrayList<>();

    public LoginResponse(String username, Object loginId, String tokenName, String tokenValue) {
        this.username = username;
        this.loginId = loginId;
        this.tokenName = tokenName;
        this.tokenValue = tokenValue;
    }

    public LoginResponse(String username, Object loginId, String tokenName, String tokenValue,
                         String loginSessionId, Long userId, String userCode, String usernameMasked, String realNameMasked,
                         String roleCode, String roleName, List<String> permissions, List<MenuVO> menus) {
        this(username, loginId, tokenName, tokenValue);
        this.loginSessionId = loginSessionId;
        this.userId = userId;
        this.userCode = userCode;
        this.usernameMasked = usernameMasked;
        this.realNameMasked = realNameMasked;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.permissions = permissions;
        this.menus = menus;
    }

    public String getUsername() {
        return username;
    }

    public Object getLoginId() {
        return loginId;
    }

    public String getTokenName() {
        return tokenName;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public String getLoginSessionId() {
        return loginSessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserCode() {
        return userCode;
    }

    public String getUsernameMasked() {
        return usernameMasked;
    }

    public String getRealNameMasked() {
        return realNameMasked;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public List<MenuVO> getMenus() {
        return menus;
    }
}
