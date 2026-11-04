package com.syt.blog.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。核心原则——两类异常，两种待遇：
 * <ul>
 *   <li><b>预期业务异常</b>（BusinessException 及子类、参数校验失败）：message 面向用户，原样返回；warn 日志。</li>
 *   <li><b>非预期技术异常</b>（RuntimeException / Exception / 解析 / 超限）：绝不把 e.getMessage() 回前端，
 *       统一返回通用提示，完整堆栈只进 error 日志，前端凭 HTTP 状态码分类展示。</li>
 * </ul>
 * 严禁在技术异常的响应体里放 e.getMessage()——那是 SQL / 类名 / 路径，既泄露信息又对用户无意义。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 预期业务异常：message 安全，原样返回 ====================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(ResourceNotFoundException e) {
        log.warn("资源不存在 code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        // 业务码不一定是合法 HTTP 状态码（如 AUTH_FAILED=401004），取前三位作 HTTP 状态，
        // 完整业务码保留在响应体 Result.code 中供前端精确区分。
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /** @RequestBody 校验失败 → 400，拼出所有字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.error(400, msg));
    }

    /** @RequestParam / @PathVariable 校验失败 → 400，格式化为「字段: 提示」 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> propertyTail(v) + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.error(400, msg));
    }

    /** 取 ConstraintViolation 路径最后一段作字段名（updatePost.id → id） */
    private String propertyTail(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /** 撞唯一约束等完整性冲突 → 400 通用提示（精确语义由 Service 用 DuplicateNameException 区分） */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突", e);
        return ResponseEntity.badRequest()
                .body(Result.error(400, "数据冲突，请检查提交内容是否重复或缺失必填项"));
    }

    // ==================== 常见技术异常：友好提示 + 正确 HTTP，不泄露细节 ====================

    /** 请求体 JSON 解析失败 → 400，不暴露 Jackson 解析细节 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败", e);
        return ResponseEntity.badRequest()
                .body(Result.error(400, "请求体格式错误或缺失，请检查 JSON 是否合法"));
    }

    /** 缺少必填请求参数 → 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(Result.error(400, "缺少必填参数：" + e.getParameterName()));
    }

    /** 参数类型转换失败（?id=abc 转 Integer）→ 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(Result.error(400, "参数格式不正确：" + e.getName()));
    }

    /** 请求方法不支持（GET 调 POST 接口）→ 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error(405, "请求方法不支持：" + e.getMethod()));
    }

    /** 路径不存在 → 404 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNoHandler(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "接口不存在"));
    }

    /** 上传文件超限 → 413，不暴露 OSS / multipart 内部信息 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(413, "上传文件过大，请压缩或减小后重试"));
    }

    // ==================== 兜底：非预期异常，绝不泄露 message ====================

    /**
     * 未捕获的 RuntimeException（NPE、DB 连接断开、查询超时、SSE 中断等）→ 500 通用提示。
     * 不返回 e.getMessage()：可能含 SQL / 类名 / 路径。完整堆栈进 error 日志，前端只看到「服务器繁忙」。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Void>> handleRuntime(RuntimeException e) {
        log.error("未处理 RuntimeException", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器繁忙，请稍后重试"));
    }

    /** 最终兜底 → 500，不向用户暴露任何细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器繁忙，请稍后重试"));
    }

    /** 业务码映射为合法 HTTP 状态码：标准（100-599）原样，否则取前三位（401004 → 401） */
    private int toHttpStatus(int code) {
        return (code >= 100 && code <= 599) ? code : code / 1000;
    }
}
