package com.elibrary.book.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Book 聚合规则单测
 *
 * @author link
 * @date 2026/10/19
 */
class BookTest {

    /** 借出扣减一个可借名额 */
    @Test
    void borrowDecrementsAvailableLicenses() {
        Book book = book(3);
        book.borrow();
        assertThat(book.getAvailableLicenses()).isEqualTo(2);
    }

    /** 名额耗尽时借出抛 NoLicenseException */
    @Test
    void borrowThrowsNoLicenseWhenLicensesExhausted() {
        Book book = book(0);
        assertThatThrownBy(book::borrow).isInstanceOf(NoLicenseException.class);
    }

    /** 归还释放回一个可借名额 */
    @Test
    void releaseIncrementsAvailableLicenses() {
        Book book = book(3, 1);
        book.release();
        assertThat(book.getAvailableLicenses()).isEqualTo(2);
    }

    /** 名额已满时释放抛 IllegalStateException（程序错误而非业务异常） */
    @Test
    void releaseThrowsIllegalStateWhenAlreadyFull() {
        // 双放名额是调用方 bug（loan 状态机已挡），不是业务分支，故用 IllegalStateException 而非领域异常
        Book book = book(3, 3);
        assertThatThrownBy(book::release).isInstanceOf(IllegalStateException.class);
    }

    private Book book(int total) { return book(total, total); }

    private Book book(int total, int available) {
        Book book = new Book();
        book.setTitle("重构");
        book.setAuthor("Martin Fowler");
        book.setCategory("TECHNOLOGY");
        book.setTotalLicenses(total);
        book.setAvailableLicenses(available);
        return book;
    }
}
