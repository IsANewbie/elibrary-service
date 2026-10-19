package com.elibrary.loan.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loan 聚合规则单测
 *
 * @author link
 * @date 2026/10/19
 */
class LoanTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Test
    void 借期固定14天() {
        Loan loan = Loan.borrow(1L, 2L, now);
        assertThat(loan.getDueAt()).isEqualTo(now.plusDays(14));
        assertThat(loan.getBorrowedAt()).isEqualTo(now);
    }

    @Test
    void 未归还即在借() {
        Loan loan = Loan.borrow(1L, 2L, now);
        assertThat(loan.isActive()).isTrue();
        assertThat(loan.isOverdue(now.plusDays(13))).isFalse();
        assertThat(loan.isOverdue(now.plusDays(15))).isTrue();
    }

    @Test
    void 归还后不再在借也不逾期() {
        Loan loan = Loan.borrow(1L, 2L, now);
        loan.markReturned(now.plusDays(3));
        assertThat(loan.isActive()).isFalse();
        assertThat(loan.isOverdue(now.plusDays(20))).isFalse();
    }

    @Test
    void 重复归还抛领域异常() {
        Loan loan = Loan.borrow(1L, 2L, now);
        loan.markReturned(now.plusDays(3));
        assertThatThrownBy(() -> loan.markReturned(now.plusDays(4)))
                .isInstanceOf(LoanAlreadyReturnedException.class);
    }
}
