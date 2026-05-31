package jimmy.logistics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解 —— 标记需要审计的接口方法。
 * <p>
 * 标注在 Controller 方法上，{@link jimmy.logistics.config.OperationLogInterceptor}
 * 会在请求完成后自动记录操作人、接口路径、耗时和异常信息。
 * 也可标注在 Controller 类上作为该 Controller 下所有方法的默认操作描述。
 * </p>
 *
 * <pre>
 * // 方法级标注
 * &#64;OperationLog("创建物流订单")
 * &#64;PostMapping("/orders")
 * public ApiResponse create() { ... }
 *
 * // 类级标注（作为默认值）
 * &#64;OperationLog("用户管理")
 * &#64;RestController
 * public class UserController { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 操作描述，将写入操作日志表的 operation 字段 */
    String value();
}
