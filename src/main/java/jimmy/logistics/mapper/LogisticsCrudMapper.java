package jimmy.logistics.mapper;

import jimmy.logistics.model.CrudFieldValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsCrudMapper {

    int insertRecord(@Param("tableName") String tableName,
                     @Param("fields") List<CrudFieldValue> fields);

    int updateRecord(@Param("tableName") String tableName,
                     @Param("id") Long id,
                     @Param("fields") List<CrudFieldValue> fields,
                     @Param("increaseVersion") boolean increaseVersion);

    int logicalDelete(@Param("tableName") String tableName,
                      @Param("id") Long id,
                      @Param("fields") List<CrudFieldValue> fields,
                      @Param("increaseVersion") boolean increaseVersion);

    int physicalDelete(@Param("tableName") String tableName,
                       @Param("id") Long id);

    int countByBusinessCode(@Param("tableName") String tableName,
                            @Param("columnName") String columnName,
                            @Param("code") String code);

    Long selectCustomerIdFromOrdersByName(@Param("customerName") String customerName);

    Long selectCustomerIdByName(@Param("customerName") String customerName);

    int insertCustomerForAccount(@Param("id") Long id,
                                 @Param("customerCode") String customerCode,
                                 @Param("customerName") String customerName,
                                 @Param("now") java.sql.Timestamp now);

    int updateOrderCustomerIdByName(@Param("customerId") Long customerId,
                                    @Param("customerName") String customerName);

    Long selectRoleIdByCode(@Param("roleCode") String roleCode);

    String selectRoleCodeById(@Param("roleId") Long roleId);

    int countCustomerAccounts(@Param("customerId") Long customerId,
                              @Param("excludeUserId") Long excludeUserId);

    int countUserByUsername(@Param("username") String username);

    int countUserByMobile(@Param("plainMobile") String plainMobile,
                          @Param("encryptedMobile") String encryptedMobile);

    int countPersonalCustomerByMobile(@Param("plainMobile") String plainMobile,
                                      @Param("encryptedMobile") String encryptedMobile);

    int countEnterpriseMainAccountByCustomerName(@Param("customerName") String customerName);
}
