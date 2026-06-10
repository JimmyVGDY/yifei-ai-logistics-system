package jimmy.system.entity;

import java.time.LocalDateTime;

/**
 * 演示用户实体 —— 最简单的用户表映射，用于基础设施连通性验证。
 */
public class DemoUser {

    /** 主键ID */
    private Long id;
    /** 用户名 */
    private String username;
    /** 显示名称 */
    private String displayName;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
