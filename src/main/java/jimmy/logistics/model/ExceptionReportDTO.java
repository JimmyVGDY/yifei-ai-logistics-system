package jimmy.logistics.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 异常上报请求 —— 前端提交运输异常信息。
 * <p>
 * 三个字段均必填，通过 {@code @NotBlank} 注解在 Controller 层完成校验。
 * </p>
 */
public class ExceptionReportDTO {

    /** 关联的物流订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 异常类型（地址错误/货损/延误/客户拒收/车辆故障/其他） */
    @NotBlank(message = "异常类型不能为空")
    private String exceptionType;

    /** 异常详细描述 */
    @NotBlank(message = "异常描述不能为空")
    private String exceptionDesc;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getExceptionDesc() { return exceptionDesc; }
    public void setExceptionDesc(String exceptionDesc) { this.exceptionDesc = exceptionDesc; }
}
