package jimmy.mapper;

import jimmy.entity.LoginHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 登录历史 Mapper —— 记录和查询登录尝试，支撑异常登录检测。
 * <p>
 * 提供按用户+时间范围查询成功登录记录的能力，用于判断 IP/UA 是否为常用设备。
 */
@Mapper
public interface LoginHistoryMapper {

    /**
     * 插入一条登录历史记录。
     *
     * @param history 登录历史实体
     */
    void insert(LoginHistory history);

    /**
     * 查询用户最近 N 天内的成功登录记录。
     * <p>用于设备熟悉度检查：判断当前 IP/UA 是否在该用户的历史使用中出现过。
     *
     * @param userId 用户ID
     * @param days   近 N 天
     * @return 成功登录的历史记录列表（仅 IP 和 UA 字段有值）
     */
    List<LoginHistory> selectRecentSuccessLogins(@Param("userId") Long userId, @Param("days") int days);
}
