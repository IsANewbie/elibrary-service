package com.elibrary.common.api;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * RFC 7807 全局异常映射，errorCode→HTTP 唯一真相
 *
 * @author link
 * @date 2026/10/19
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<ErrorCode, HttpStatus> STATUS_BY_CODE = Map.of(
            ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
            ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
            ErrorCode.BOOK_NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCode.LOAN_NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCode.BOOK_NO_LICENSE, HttpStatus.CONFLICT,
            ErrorCode.LOAN_LIMIT_REACHED, HttpStatus.CONFLICT,
            ErrorCode.LOAN_DUPLICATE, HttpStatus.CONFLICT,
            ErrorCode.LOAN_ALREADY_RETURNED, HttpStatus.CONFLICT,
            ErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT);
    // 注意：Map.of 上限 10 对，第 10 个 code 加入时换 Map.ofEntries 或 EnumMap

    static {
        var unmapped = java.util.EnumSet.allOf(ErrorCode.class);
        STATUS_BY_CODE.keySet().forEach(unmapped::remove);
        if (!unmapped.isEmpty()) {
            throw new IllegalStateException("ErrorCode 未映射 HTTP 状态: " + unmapped);
        }
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        HttpStatus status = STATUS_BY_CODE.get(ex.getCode());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("errorCode", ex.getCode().name());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldErrorWrapper::fieldOf, FieldErrorWrapper::messageOf, (a, b) -> a));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求参数校验失败");
        pd.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        pd.setProperty("errorCode", ErrorCode.VALIDATION_FAILED.name());
        pd.setProperty("fields", fields);
        return pd;
    }

    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class})
    public ProblemDetail handleBadRequest(Exception ex) {
        // 不回拼原始 message：类型不匹配/解析失败的内部信息不属于 API 契约
        String detail = "请求参数非法";
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            detail = "参数 " + mismatch.getName() + " 的值非法";
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        pd.setProperty("errorCode", ErrorCode.VALIDATION_FAILED.name());
        return pd;
    }

    // 未映射路由在 Spring 6 落进静态资源处理器抛 NoResourceFoundException，不能被兜底吞成 500
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "资源不存在");
        pd.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknown(Exception ex) {
        // Spring 内置协议异常（405/415/缺参等 ErrorResponse）按原生状态返回，不吞成 500
        if (ex instanceof org.springframework.web.ErrorResponse er) {
            log.warn("请求处理失败: {}", ex.getMessage());
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(status,
                    er.getBody().getDetail() != null ? er.getBody().getDetail() : "请求方法或媒体类型不被接受");
            pd.setTitle(status.getReasonPhrase());
            return pd;
        }
        log.error("未预期异常", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误");
        pd.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        return pd;
    }

    // FieldError 的 getMessage 可能为 null，收拢在这里
    private static final class FieldErrorWrapper {
        static String fieldOf(org.springframework.validation.FieldError e) { return e.getField(); }
        static String messageOf(org.springframework.validation.FieldError e) {
            return e.getDefaultMessage() == null ? "非法" : e.getDefaultMessage();
        }
    }
}
