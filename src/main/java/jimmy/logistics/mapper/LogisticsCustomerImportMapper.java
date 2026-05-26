package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LogisticsCustomerImportMapper {

    int insertImportedCustomer(@Param("id") Long id,
                               @Param("customerCode") String customerCode,
                               @Param("customerName") String customerName,
                               @Param("contactName") String contactName,
                               @Param("contactPhone") String contactPhone,
                               @Param("province") String province,
                               @Param("city") String city,
                               @Param("address") String address);
}
