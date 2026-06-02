package jimmy.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import jimmy.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

/**
 * 全局异常处理器 —— 统一将异常转换为 {@link ApiResponse} 标准 JSON 响应。
 * <p>
 * 覆盖认证(401)、鉴权(403)、参数校验(400)、数据库(500)、未知(500)异常，
 * 保证前端永远不会收到 HTML 错误页。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 未登录统一返回 401，前端拦截后会清理 token 并跳转到登录页。
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleNotLogin(NotLoginException exception) {
        log.warn("接口访问未登录，reason={}", exception.getMessage());
        return ApiResponse.failure(401, "请先登录后再访问");
    }

    // Sa-Token 的其它鉴权异常也按认证失败处理，避免接口返回 HTML 错误页。
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleNotPermission(NotPermissionException exception) {
        log.warn("Sa-Token 鉴权异常，reason={}", exception.getMessage());
        return ApiResponse.failure(403, exception.getMessage());
    }

    @ExceptionHandler(SaTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleSaToken(SaTokenException exception) {
        log.warn("Sa-Token 鉴权异常，reason={}", exception.getMessage());
        return ApiResponse.failure(401, exception.getMessage());
    }

    // 业务参数校验失败统一返回 400，便于前端直接展示错误原因。
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("业务参数校验失败，reason={}", exception.getMessage());
        return ApiResponse.failure(400, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
                ? "请求参数不合法"
                : exception.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        log.warn("DTO 参数校验失败，reason={}", message);
        return ApiResponse.failure(400, message);
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMultipart(MultipartException exception) {
        log.warn("文件上传请求不合法，reason={}", exception.getMessage());
        return ApiResponse.failure(400, "文件上传请求不合法，请重新选择文件");
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleDataAccess(DataAccessException exception) {
        log.error("数据库操作异常", exception);
        return ApiResponse.failure(500, "数据库操作失败，请检查数据是否完整");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("系统未知异常", exception);
        return ApiResponse.failure(500, "系统异常，请稍后再试");
    }
}
