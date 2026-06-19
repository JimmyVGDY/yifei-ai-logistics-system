package jimmy.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要列级字段过滤的 Controller 方法。
 * <p>
 * {@link ColumnFilterAdvice} 读取此注解，根据当前用户的列权限
 * 从响应数据中移除无权限查看的字段。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 *   @ColumnScope(module = "order", paths = {"records"})
 *   @GetMapping("/modules/orders")
 *   public ApiResponse<PageResult<Map>> list(...) { ... }
 *
 *   @ColumnScope(module = "dashboard")
 *   @GetMapping("/dashboard")
 *   public ApiResponse<DashboardVO> dashboard() { ... }
 * }</pre>
 *
 * @see ColumnFilterAdvice
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ColumnScope {

    /** 模块标识，对应权限码中的 module 部分，如 "order" */
    String module();

    /**
     * 需要过滤的 JSON 路径列表（相对于 ApiResponse.data）。
     * 为空时递归过滤整个 data 对象。
     * 例如 {"records"} 表示只过滤 data.records 下的字段。
     */
    String[] paths() default {};
}
