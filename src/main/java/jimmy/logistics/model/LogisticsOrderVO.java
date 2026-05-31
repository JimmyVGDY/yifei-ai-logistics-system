package jimmy.logistics.model;

import jimmy.logistics.entity.LogisticsOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物流订单前端展示对象 —— 剥离了内部 ID 和冗余字段，只返回前端需要的业务数据。
 * <p>
 * 通过静态工厂方法 {@link #from(LogisticsOrder)} 从实体转换，
 * 状态码额外提供 {@code statusLabel} 中文标签便于前端直接渲染。
 * </p>
 */
public class LogisticsOrderVO {

    private Long id;
    /** 订单号（服务端生成的唯一业务编号） */
    private String orderNo;
    /** 客户名称 */
    private String customerName;
    /** 发货地址 */
    private String senderAddress;
    /** 收货地址 */
    private String receiverAddress;
    /** 货物名称 */
    private String cargoName;
    /** 货物重量（千克） */
    private BigDecimal cargoWeight;
    /** 订单状态（英文状态码） */
    private String status;
    /** 订单状态中文标签 */
    private String statusLabel;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 从实体对象构建 VO，null 安全 */
    public static LogisticsOrderVO from(LogisticsOrder order) {
        if (order == null) {
            return null;
        }
        LogisticsOrderVO vo = new LogisticsOrderVO();
        vo.setId(order.getId());        vo.setOrderNo(order.getOrderNo());
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
