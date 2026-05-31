package jimmy.logistics.model;

/**
 * 异常处理请求 —— 用于异常管理页面的"开始处理"/"处理完成"操作。
 * <p>
 * {@code exceptionStatus} 可选值：
 * <ul>
 *   <li>{@code PROCESSING} — 开始处理（当前状态必须为 WAIT_HANDLE）</li>
 *   <li>{@code CLOSED} — 处理完成（当前状态必须为 WAIT_HANDLE 或 PROCESSING）</li>
 * </ul>
 * 默认值为 {@code CLOSED}，兼容前端未传状态的场景。
 * </p>
 */
public class ExceptionHandleDTO {

    /** 目标异常状态，默认为已关闭 */
    private String exceptionStatus = "CLOSED";

    public String getExceptionStatus() { return exceptionStatus; }

    public void setExceptionStatus(String exceptionStatus) { this.exceptionStatus = exceptionStatus; }
}
