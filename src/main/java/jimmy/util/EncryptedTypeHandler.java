package jimmy.util;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 加密字段 TypeHandler —— 按需在 Mapper XML 中显式指定启用。
 * <p>
 * 注意：此类不注册为 {@code @Component}，避免 MyBatis 自动注册为全局 String 处理器
 * 导致所有字符串参数被误加密。敏感字段加解密由 Service 层的
 * {@code encryptValues() / decryptRecords()} 统一处理。
 * </p>
 */
public class EncryptedTypeHandler extends BaseTypeHandler<String> {

    private static FieldEncryptor encryptor;

    /** Spring 通过 setter 注入静态实例（TypeHandler 由 MyBatis 实例化，不走 Spring 容器） */
    public static void setEncryptor(FieldEncryptor instance) {
        encryptor = instance;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        if (encryptor == null) {
            ps.setString(i, parameter);
        } else {
            ps.setString(i, encryptor.encrypt(parameter));
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    private String decrypt(String value) {
        if (encryptor == null || value == null) {
            return value;
        }
        return encryptor.decrypt(value);
    }
}
