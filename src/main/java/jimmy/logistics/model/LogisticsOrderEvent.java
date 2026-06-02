package jimmy.logistics.model;

import java.time.LocalDateTime;

/**
 * 物流订单生命周期事件 —— 订单创建/状态变更后通过 RabbitMQ 广播。
 */
public class LogisticsOrderEvent {

    /** 订单事件类型（CREATED/UPDATED/STATUS_CHANGED） */
    private String eventType;
    /** 订单号 */
    private String orderNo;
    /** 事件发生时的订单状态 */
    private String status;
    /** 事件发生时间 */
    private LocalDateTime occurredAt;
    /** 请求链路ID：一次 HTTP 请求或业务链路共用 */
    private String traceId;
    /** 操作审计ID：定位单次可审计操作 */
    private String operationId;
    /** 登录会话ID：串联一次登录期间的全部行为 */
    private String loginSessionId;
    /** 当前登录用户ID，审计追踪保留原值 */
    private String userId;
    /** 当前登录用户业务编号，审计追踪保留原值 */
    private String userCode;
    /** 脱敏后的登录账号 */
    private String usernameMasked;
    /** 当前角色编码 */
    private String roleCode;

    public LogisticsOrderEvent() {
    }

    public LogisticsOrderEvent(String eventType, String orderNo, String status, LocalDateTime occurredAt) {
        this.eventType = eventType;
        this.orderNo = orderNo;
        this.status = status;
        this.occurredAt = occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getLoginSessionId() {
        return loginSessionId;
    }

    public void setLoginSessionId(String loginSessionId) {
        this.loginSessionId = loginSessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getUsernameMasked() {
        return usernameMasked;
    }

    public void setUsernameMasked(String usernameMasked) {
        this.usernameMasked = usernameMasked;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}
