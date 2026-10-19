package com.elibrary.common.domain;

import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.domain.NoLicenseException;
import com.elibrary.loan.domain.LoanAlreadyReturnedException;
import com.elibrary.loan.domain.LoanDuplicateException;
import com.elibrary.loan.domain.LoanLimitException;
import com.elibrary.loan.domain.LoanNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionTest {

    @Test
    void 异常携带稳定errorCode() {
        var ex = new LoanNotFoundForTest("loan 不存在");
        assertThat(ex.getCode()).isEqualTo(ErrorCode.LOAN_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("loan 不存在");
    }

    @Test
    void cause链构造器保留根因() {
        var root = new IllegalStateException("uq_loans_user_active_book");
        var ex = new CauseForTest("重复借阅", root);
        assertThat(ex.getCause()).isSameAs(root);
    }

    // 构造器绑定哪个 code 编译器不查，贴错则 HTTP 状态与 errorCode 双错，全覆盖兜底
    @ParameterizedTest
    @MethodSource("subclassBindings")
    void 子类绑定正确的ErrorCode(DomainException ex, ErrorCode expected) {
        assertThat(ex.getCode()).isEqualTo(expected);
    }

    static Stream<Arguments> subclassBindings() {
        return Stream.of(
                Arguments.of(new BookNotFoundException("x"), ErrorCode.BOOK_NOT_FOUND),
                Arguments.of(new NoLicenseException("x"), ErrorCode.BOOK_NO_LICENSE),
                Arguments.of(new LoanNotFoundException("x"), ErrorCode.LOAN_NOT_FOUND),
                Arguments.of(new LoanLimitException("x"), ErrorCode.LOAN_LIMIT_REACHED),
                Arguments.of(new LoanDuplicateException("x"), ErrorCode.LOAN_DUPLICATE),
                Arguments.of(new LoanAlreadyReturnedException("x"), ErrorCode.LOAN_ALREADY_RETURNED),
                Arguments.of(new OptimisticLockConflictException("x"), ErrorCode.CONCURRENT_MODIFICATION));
    }

    static class LoanNotFoundForTest extends DomainException {
        LoanNotFoundForTest(String message) { super(ErrorCode.LOAN_NOT_FOUND, message); }
    }

    static class CauseForTest extends DomainException {
        CauseForTest(String message, Throwable cause) { super(ErrorCode.LOAN_DUPLICATE, message, cause); }
    }
}
