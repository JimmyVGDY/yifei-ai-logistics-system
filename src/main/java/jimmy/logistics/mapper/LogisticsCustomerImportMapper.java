package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 客户 Excel 导入数据访问 —— 批量写入从 Excel 解析出的客户记录。
 */
@Mapper
public interface LogisticsCustomerImportMapper {

    /** 插入从 Excel 导入的客户数据 */
    int insertImportedCustomer(@Param("id") Long id,
                               @Param("customerCode") String customerCode,
                               @Param("customerName") String customerName,
                               @Param("contactName") String contactName,
                               @Param("contactPhone") String contactPhone,
                               @Param("province") String province,
                               @Param("city") String city,
                               @Param("address") String address);
}
