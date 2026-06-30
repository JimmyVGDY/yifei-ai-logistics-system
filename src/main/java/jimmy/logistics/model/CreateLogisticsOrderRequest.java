package jimmy.logistics.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;

/**
 * 物流订单创建请求 —— 客户/地址/货物信息，JSR-303 校验。
 */
public class CreateLogisticsOrderRequest {

    /** 客户名称 */
    @NotBlank(message = "客户名称不能为空")
    private String customerName;
    /** 发货地址 */
    @NotBlank(message = "发货地址不能为空")
    private String senderAddress;
    /** 收货地址 */
    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;
    /** 货物名称 */
    private String cargoName;
    /** 货物重量（千克） */
    private BigDecimal cargoWeight;
    /** 可选幂等键，支持同一创建请求重试不重复下单 */
    private String idempotencyKey;

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
