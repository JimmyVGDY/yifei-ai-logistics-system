package jimmy.logistics.model;

import jimmy.logistics.entity.LogisticsOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LogisticsOrderVO {

    private Long id;
    private String orderNo;
    private String customerName;
    private String senderAddress;
    private String receiverAddress;
    private String cargoName;
    private BigDecimal cargoWeight;
    private String status;
    private String statusLabel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LogisticsOrderVO from(LogisticsOrder order) {
        if (order == null) {
            return null;
        }
        LogisticsOrderVO vo = new LogisticsOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setCustomerName(order.getCustomerName());
        vo.setSenderAddress(order.getSenderAddress());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setCargoName(order.getCargoName());
        vo.setCargoWeight(order.getCargoWeight());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(StatusLabel.label(order.getStatus()));
        vo.setCreatedAt(order.getCreatedAt());
        vo.setUpdatedAt(order.getUpdatedAt());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
