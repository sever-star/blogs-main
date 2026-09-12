package com.syt.blog.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(ResourceNotFoundException e){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error(e.getCode(), e.getMessage()));
    }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e){
        // 业务码不一定是合法 HTTP 状态码（如 AUTH_FAILED=401004），取前三位作为 HTTP 状态，
        // 完整业务码仍保留在响应体 Result.code 中，供前端精确区分错误。
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 将业务码映射为合法 HTTP 状态码。
     * <p>标准 HTTP 状态（100-599）原样使用；否则取业务码前三位（401004 → 401）。
     */
    private int toHttpStatus(int code) {
        return (code >= 100 && code <= 599) ? code : code / 1000;
    }
    /**Controller层校验失败 → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e){
        // 获取校验失败的字段和错误信息
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Result.error(400, msg));
    }

    /** Service 层校验（如 @RequestParam 校验）失败 → 400 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest()//等价ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, e.getMessage()));
    }

    /** 并发撞唯一约束 → 400，DB 兜底 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "标签名已存在"));
    }

    /** 兜底 → 500，不向用户暴露堆栈 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器内部错误"));
    }
}
