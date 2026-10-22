package com.omes.dgw.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusiness(BusinessException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(message);
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleAuth(AuthenticationException ex) {
        return Result.fail(401, "用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleAccessDenied() {
        return Result.fail(403, "无权限访问");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleIllegalArgument(Exception ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("协议")) {
            return Result.fail(message);
        }
        if (ex instanceof MethodArgumentTypeMismatchException mismatch
                && mismatch.getRequiredType() != null
                && mismatch.getRequiredType().isEnum()) {
            return Result.fail("参数值无效: " + mismatch.getName());
        }
        return Result.fail(message != null ? message : "参数错误");
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleDuplicateKey(DataIntegrityViolationException ex) {
        if (isUniqueConstraintViolation(ex)) {
            return Result.fail("编号已存在");
        }
        return Result.fail("数据违反约束");
    }

    private boolean isUniqueConstraintViolation(Throwable ex) {
        while (ex != null) {
            String message = ex.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("unique")
                        || lower.contains("duplicate")
                        || message.contains("唯一")
                        || message.contains("UK_")) {
                    return true;
                }
            }
            ex = ex.getCause();
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception ex) {
        log.error("", ex);
        return Result.fail(500, "服务器内部错误");
    }
}
