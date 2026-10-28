package com.elibrary.loan.application;

import com.elibrary.book.api.BookFacade;
import com.elibrary.book.domain.Book;
import com.elibrary.loan.domain.Loan;
import com.elibrary.loan.domain.LoanDuplicateException;
import com.elibrary.loan.domain.LoanLimitException;
import com.elibrary.loan.mapper.LoanMapper;
import com.elibrary.user.api.UserFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 借阅校验链单测：顺序即并发设计（user 锁→书→上限→重复→扣名额→插入）
 *
 * @author link
 * @date 2026/10/20
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceBorrowTest {

    @Mock UserFacade userFacade;
    @Mock BookFacade bookFacade;
    @Mock LoanMapper loanMapper;
    @InjectMocks LoanService loanService;

    /** 校验链顺序即锁序设计：user 锁→书→上限→重复→扣名额→插入 */
    @Test
    void borrowFollowsFixedValidationChainOrder() {
        when(bookFacade.getBook(2L)).thenReturn(book(2L));
        when(loanMapper.countActiveByUser(1L)).thenReturn(0L);
        when(loanMapper.existsActiveByUserAndBook(1L, 2L)).thenReturn(false);

        Loan loan = loanService.borrow(1L, 2L);

        assertThat(loan.getUserId()).isEqualTo(1L);
        assertThat(loan.getDueAt()).isEqualTo(loan.getBorrowedAt().plusDays(14));
        // 顺序即设计：先 user 锁，再加载书，再检查，最后扣名额插记录
        InOrder order = inOrder(userFacade, bookFacade, loanMapper);
        order.verify(userFacade).lockById(1L);
        order.verify(bookFacade).getBook(2L);
        order.verify(loanMapper).countActiveByUser(1L);
        order.verify(loanMapper).existsActiveByUserAndBook(1L, 2L);
        order.verify(bookFacade).reserveLicense(any(Book.class));
        order.verify(loanMapper).insert(any(Loan.class));
    }

    /** 第 5 笔在借后再借命中上限，且不扣名额 */
    @Test
    void fifthActiveLoanBlocksBorrow() {
        when(bookFacade.getBook(2L)).thenReturn(book(2L));
        when(loanMapper.countActiveByUser(1L)).thenReturn(5L);

        assertThatThrownBy(() -> loanService.borrow(1L, 2L)).isInstanceOf(LoanLimitException.class);
        verify(bookFacade, never()).reserveLicense(any());
    }

    /** 同书已有在借时拒绝借阅，且不扣名额 */
    @Test
    void duplicateActiveBorrowThrowsConflict() {
        when(bookFacade.getBook(2L)).thenReturn(book(2L));
        when(loanMapper.countActiveByUser(1L)).thenReturn(0L);
        when(loanMapper.existsActiveByUserAndBook(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> loanService.borrow(1L, 2L)).isInstanceOf(LoanDuplicateException.class);
        verify(bookFacade, never()).reserveLicense(any());
    }

    /** 唯一索引兜底命中翻译为 LoanDuplicateException 且保留根因 cause */
    @Test
    void uniqueIndexFallbackTranslatesToLoanDuplicate() {
        when(bookFacade.getBook(2L)).thenReturn(book(2L));
        when(loanMapper.countActiveByUser(1L)).thenReturn(0L);
        when(loanMapper.existsActiveByUserAndBook(1L, 2L)).thenReturn(false);
        doThrow(new DuplicateKeyException("Duplicate entry ... for key 'uq_loans_user_active_book'"))
                .when(loanMapper).insert(any(Loan.class));

        // 并发双击穿透应用检查时，生成列唯一索引兜底
        assertThatThrownBy(() -> loanService.borrow(1L, 2L))
                .isInstanceOf(LoanDuplicateException.class)
                .hasCauseInstanceOf(DuplicateKeyException.class);
    }

    private Book book(long id) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("重构");
        book.setTotalLicenses(3);
        book.setAvailableLicenses(3);
        book.setVersion(0);
        return book;
    }
}
