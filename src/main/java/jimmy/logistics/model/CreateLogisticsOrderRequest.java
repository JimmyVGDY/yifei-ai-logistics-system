package jimmy.logistics.model;

import java.math.BigDecimal;
import javax.validation.constraints.NotBlank;

public class CreateLogisticsOrderRequest {

    @NotBlank(message = "客户名称不能为空")
    private String customerName;
    @NotBlank(message = "发货地址不能为空")
    private String senderAddress;
    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;
    private String cargoName;
    private BigDecimal cargoWeight;

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
}
