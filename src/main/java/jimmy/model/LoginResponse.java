package jimmy.model;

public class LoginResponse {

    private String username;
    private Object loginId;
    private String tokenName;
    private String tokenValue;

    public LoginResponse(String username, Object loginId, String tokenName, String tokenValue) {
        this.username = username;
        this.loginId = loginId;
        this.tokenName = tokenName;
        this.tokenValue = tokenValue;
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
}
