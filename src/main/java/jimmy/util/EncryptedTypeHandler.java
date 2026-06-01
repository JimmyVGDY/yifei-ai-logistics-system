package jimmy.util;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 加密字段 TypeHandler —— 自动对 VARCHAR 字段进行 AES 加解密。
 * <p>
 * 在 Mapper XML 中指定 {@code typeHandler=...} 即可透明加解密，业务代码无需感知。
 * 未配置加密密钥时加密操作会被跳过，写入和读出均为原文。
 * </p>
 *
 * <pre>
 * #{mobile, typeHandler=jimmy.util.EncryptedTypeHandler}
 * </pre>
 */
@Component
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
