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

    @Test
    void domainExceptionMapsToCentralizedStatus() {
        ProblemDetail pd = handler.handleDomain(new NoLicenseException("名额耗尽"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("BOOK_NO_LICENSE");
    }

    @Test
    void foreignLoanMapsToSame404AsMissing() {
        assertThat(handler.handleDomain(new LoanNotFoundException("x")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void unmatchedRouteMapsTo404Not500() {
        var ex = new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.POST, "api/auth/login");
        assertThat(handler.handleNotFound(ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

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
