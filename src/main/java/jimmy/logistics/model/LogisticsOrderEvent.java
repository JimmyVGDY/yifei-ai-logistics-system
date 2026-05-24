package jimmy.logistics.model;

import java.time.LocalDateTime;

public class LogisticsOrderEvent {

    private String eventType;
    private String orderNo;
    private String status;
    private LocalDateTime occurredAt;

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
}
