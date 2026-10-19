package com.elibrary.common.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionTest {

    @Test
    void 异常携带稳定errorCode() {
        var ex = new LoanNotFoundForTest("loan 不存在");
        assertThat(ex.getCode()).isEqualTo(ErrorCode.LOAN_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("loan 不存在");
    }

    static class LoanNotFoundForTest extends DomainException {
        LoanNotFoundForTest(String message) { super(ErrorCode.LOAN_NOT_FOUND, message); }
    }
}
