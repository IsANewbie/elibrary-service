package com.elibrary.common.api;

import com.elibrary.book.domain.NoLicenseException;
import com.elibrary.loan.domain.LoanNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异常映射单测
 *
 * @author link
 * @date 2026/10/19
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** 领域异常按集中映射表出状态码与 errorCode */
    @Test
    void domainExceptionMapsToCentralizedStatus() {
        ProblemDetail pd = handler.handleDomain(new NoLicenseException("名额耗尽"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("BOOK_NO_LICENSE");
    }

    /** LOAN_NOT_FOUND 映射 404（非本人与不存在同响应的前提） */
    @Test
    void foreignLoanMapsToSame404AsMissing() {
        assertThat(handler.handleDomain(new LoanNotFoundException("x")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    /** 未映射路由落 NoResourceFoundException 出 404，不被兜底吞成 500 */
    @Test
    void unmatchedRouteMapsTo404Not500() {
        var ex = new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.POST, "api/auth/login");
        assertThat(handler.handleNotFound(ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    /** 协议异常（405 等）保留原生状态返回，不吞成 500 */
    @Test
    void protocolExceptionsKeepNativeStatus() {
        var ex = new org.springframework.web.HttpRequestMethodNotSupportedException("GET");
        assertThat(handler.handleUnknown(ex).getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
    }

    /** 方法级校验失败归入 400 VALIDATION_FAILED 桶 */
    @Test
    void methodLevelValidationGoesTo400Bucket() throws Exception {
        // 6.2.10 无 List 构造器，用最小非空 MethodValidationResult 构造（create 断言结果非空）
        var mp = new org.springframework.core.MethodParameter(Sample.class.getDeclaredMethod("getBookId"), -1);
        var error = new org.springframework.context.support.DefaultMessageSourceResolvable(
                new String[]{"NotNull"}, "不能为空");
        var pvr = new org.springframework.validation.method.ParameterValidationResult(mp, null, java.util.List.of(error));
        var result = org.springframework.validation.method.MethodValidationResult.create(
                new Sample(), Sample.class.getDeclaredMethod("getBookId"), java.util.List.of(pvr));
        var ex = new org.springframework.web.method.annotation.HandlerMethodValidationException(result);
        ProblemDetail pd = handler.handleBadRequest(ex);
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("VALIDATION_FAILED");
        assertThat(pd.getDetail()).isEqualTo("请求参数非法");
    }

    /** body 校验失败 400 且 fields 携带字段级错误明细 */
    @Test
    void bodyValidationFailureReturns400WithFieldDetails() throws Exception {
        var ctor = Sample.class.getConstructor(String.class);
        var binding = new BeanPropertyBindingResult(new Sample(), "request");
        binding.rejectValue("bookId", "NotNull", "不能为空");
        var ex = new MethodArgumentNotValidException(new MethodParameter(ctor, 0), binding);

        ProblemDetail pd = handler.handleBodyValidation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("VALIDATION_FAILED");
        assertThat(pd.getProperties().get("fields")).isEqualTo(Map.of("bookId", "不能为空"));
    }

    static class Sample {
        private String bookId;

        public Sample() {}
        public Sample(String ignored) {}

        public String getBookId() { return bookId; }
        public void setBookId(String bookId) { this.bookId = bookId; }
    }
}
