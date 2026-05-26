package jimmy.model;

import java.util.ArrayList;
import java.util.List;

public class LoginResponse {

    private String username;
    private Object loginId;
    private String tokenName;
    private String tokenValue;
    private Long userId;
    private String userCode;
    private String usernameMasked;
    private String realNameMasked;
    private String roleCode;
    private String roleName;
    private List<String> permissions = new ArrayList<>();
    private List<MenuVO> menus = new ArrayList<>();

    public LoginResponse(String username, Object loginId, String tokenName, String tokenValue) {
        this.username = username;
        this.loginId = loginId;
        this.tokenName = tokenName;
        this.tokenValue = tokenValue;
    }

    public LoginResponse(String username, Object loginId, String tokenName, String tokenValue,
                         Long userId, String userCode, String usernameMasked, String realNameMasked,
                         String roleCode, String roleName, List<String> permissions, List<MenuVO> menus) {
        this(username, loginId, tokenName, tokenValue);
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
