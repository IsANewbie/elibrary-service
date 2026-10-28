package com.elibrary.loan.application;

import com.elibrary.book.api.BookFacade;
import com.elibrary.book.domain.Book;
import com.elibrary.loan.domain.Loan;
import com.elibrary.loan.domain.LoanAlreadyReturnedException;
import com.elibrary.loan.domain.LoanNotFoundException;
import com.elibrary.loan.mapper.LoanMapper;
import com.elibrary.loan.mapper.LoanWithBookView;
import com.elibrary.user.api.UserFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 归还/在借列表单测：非本人 404 防探测、overdue 派生
 *
 * @author link
 * @date 2026/10/20
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceReturnTest {

    @Mock UserFacade userFacade;
    @Mock BookFacade bookFacade;
    @Mock LoanMapper loanMapper;
    @InjectMocks LoanService loanService;

    /** 非本人 loan 与不存在同抛 404，防 loan id 越权探测 */
    @Test
    void foreignLoanAndMissingLoanBothReturn404() {
        // id=1 的借阅属于 bob(userId=2)，alice(1) 访问 → 与不存在同响应，防 loan id 越权探测
        when(loanMapper.selectById(1L)).thenReturn(activeLoan(1L, 2L, 2L));
        when(loanMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> loanService.returnLoan(1L, 1L)).isInstanceOf(LoanNotFoundException.class);
        assertThatThrownBy(() -> loanService.returnLoan(1L, 9L)).isInstanceOf(LoanNotFoundException.class);
        verify(bookFacade, never()).releaseLicense(org.mockito.ArgumentMatchers.any());
    }

    /** 重复归还抛 409 且名额不双放 */
    @Test
    void alreadyReturnedLoanThrowsConflict() {
        Loan returned = activeLoan(1L, 1L, 2L);
        returned.markReturned(LocalDateTime.now(ZoneOffset.UTC));
        when(loanMapper.selectById(1L)).thenReturn(returned);

        assertThatThrownBy(() -> loanService.returnLoan(1L, 1L))
                .isInstanceOf(LoanAlreadyReturnedException.class);
        verify(bookFacade, never()).releaseLicense(org.mockito.ArgumentMatchers.any());
    }

    /** 归还成功路径：落 returnedAt 并释放名额 */
    @Test
    void returnUpdatesLoanAndReleasesLicense() {
        Loan loan = activeLoan(1L, 1L, 2L);
        when(loanMapper.selectById(1L)).thenReturn(loan);
        when(loanMapper.markReturned(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);
        Book book = new Book();
        book.setId(2L);
        book.setTitle("重构");
        book.setAuthor("Martin Fowler");
        when(bookFacade.getBook(2L)).thenReturn(book);

        var response = loanService.returnLoan(1L, 1L);

        assertThat(response.returnedAt()).isNotNull();
        verify(bookFacade).releaseLicense(2L);
    }

    /** 在借列表 overdue 标志由 dueAt 派生（已过 dueAt 即 true） */
    @Test
    void currentLoansDeriveOverdueFlag() {
        LoanWithBookView overdue = new LoanWithBookView();
        overdue.setId(1L);
        overdue.setBookId(2L);
        overdue.setTitle("重构");
        overdue.setAuthor("Martin Fowler");
        overdue.setBorrowedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(20));
        overdue.setDueAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(6)); // 已过 dueAt
        when(loanMapper.selectActiveWithBook(1L)).thenReturn(List.of(overdue));

        var loans = loanService.currentLoans(1L);

        assertThat(loans).hasSize(1);
        assertThat(loans.get(0).overdue()).isTrue();
    }

    private Loan activeLoan(long id, long userId, long bookId) {
        Loan loan = Loan.borrow(userId, bookId, LocalDateTime.now(ZoneOffset.UTC));
        loan.setId(id);
        return loan;
    }
}
