package jimmy.logistics.repository;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

/**
 * ES 物流订单索引文档 —— Elasticsearch {@code @Document} 映射实体。
 */
@Document(indexName = "logistics_order")
public class LogisticsOrderSearchDocument {

    @Id
    private String orderNo;

    @Field(type = FieldType.Long)
    private Long customerId;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String customerName;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String receiverAddress;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String cargoName;

    @Field(type = FieldType.Double)
    private BigDecimal cargoWeight;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public String getCargoName() {
        return cargoName;
    }

    public void setCargoName(String cargoName) {
        this.cargoName = cargoName;
    }

    public BigDecimal getCargoWeight() {
        return cargoWeight;
    }

    public void setCargoWeight(BigDecimal cargoWeight) {
        this.cargoWeight = cargoWeight;
    }
}
