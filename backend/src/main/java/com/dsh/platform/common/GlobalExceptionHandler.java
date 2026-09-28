package com.dsh.platform.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDup(DuplicateKeyException e) {
        String msg = e.getMostSpecificCause() == null ? "" : String.valueOf(e.getMostSpecificCause().getMessage());
        String lower = msg.toLowerCase();
        if (lower.contains("uk_phone") || (lower.contains("sys_user") && lower.contains("phone"))) {
            return R.fail("该手机号已注册");
        }
        if (lower.contains("uk_credit_code") || lower.contains("credit_code")) {
            return R.fail("该企业已注册");
        }
        return R.fail("数据已存在");
    }

    /** Bean Validation 失败：只回第一条业务文案，不暴露字段名。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("参数校验失败");
        return R.fail(msg);
    }

    /** 请求体不是合法 JSON / 类型不匹配：按参数错误处理，不当系统异常。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleUnreadable(HttpMessageNotReadableException e) {
        log.debug("请求体解析失败: {}", e.getMessage());
        return R.fail("请求参数格式不正确");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(R.fail(403, "无权限访问"));
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.fail(500, "系统异常: " + e.getMessage());
    }
}
