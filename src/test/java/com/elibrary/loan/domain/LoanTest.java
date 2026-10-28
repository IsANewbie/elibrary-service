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

    /** 借出时 dueAt = borrowedAt + 14 天（借期唯一来源 LoanPolicy） */
    @Test
    void borrowSetsDueAtFourteenDaysLater() {
        Loan loan = Loan.borrow(1L, 2L, now);
        assertThat(loan.getDueAt()).isEqualTo(now.plusDays(14));
        assertThat(loan.getBorrowedAt()).isEqualTo(now);
    }

    /** 在借仅过 dueAt 后才算逾期（借出后 13 天未逾期、15 天已逾期） */
    @Test
    void activeLoanOverdueOnlyAfterDueDate() {
        Loan loan = Loan.borrow(1L, 2L, now);
        assertThat(loan.isActive()).isTrue();
        assertThat(loan.isOverdue(now.plusDays(13))).isFalse();
        assertThat(loan.isOverdue(now.plusDays(15))).isTrue();
    }

    /** now == dueAt 不算逾期：严格大于口径的边界钉死 */
    @Test
    void overdueBoundaryIsStrictlyAfterDueDate() {
        // 口径契约：now == dueAt 不算逾期（严格大于），钉死与列表派生共用的判定
        Loan loan = Loan.borrow(1L, 2L, now);
        assertThat(loan.isOverdue(now.plusDays(14))).isFalse();
    }

    /** 已归还即 inactive 且永不逾期（isOverdue 的 active 守卫） */
    @Test
    void returnedLoanIsInactiveAndNotOverdue() {
        Loan loan = Loan.borrow(1L, 2L, now);
        loan.markReturned(now.plusDays(3));
        assertThat(loan.isActive()).isFalse();
        assertThat(loan.isOverdue(now.plusDays(20))).isFalse();
    }

    /** 聚合层重复归还抛 LoanAlreadyReturnedException（SQL 条件更新前的第一道守卫） */
    @Test
    void markReturnedTwiceThrowsAlreadyReturned() {
        Loan loan = Loan.borrow(1L, 2L, now);
        loan.markReturned(now.plusDays(3));
        assertThatThrownBy(() -> loan.markReturned(now.plusDays(4)))
                .isInstanceOf(LoanAlreadyReturnedException.class);
    }
}
