package jimmy.mapper;

import jimmy.model.MenuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 认证模块数据访问 —— 登录用户查询、密码更新和菜单权限查询。
 * <p>
 * 查询结果以 {@code Map<String, Object>} 返回而非强类型实体，
 * 是为了兼容增量迁移时字段变化（如 user_code、role_code 等扩展字段）。
 * </p>
 */
@Mapper
public interface AuthMapper {

    /** 按登录用户名查询用户信息（含角色） */
    Map<String, Object> findLoginUserByUsername(@Param("username") String username);

    /** 按用户 ID 查询用户信息（含角色），用于会话恢复 */
    Map<String, Object> findLoginUserById(@Param("userId") Long userId);

    /** 更新用户密码（明文升级为 BCrypt） */
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    /** 按角色 ID 查询关联的菜单权限列表 */
    List<MenuVO> selectMenusByRoleId(@Param("roleId") Long roleId);

    /** 根据最终权限码反查可见菜单，用于用户级特殊授权后动态补齐页面入口。 */
    List<MenuVO> selectMenusByPermissionCodes(@Param("permissionCodes") List<String> permissionCodes);
}
