package com.elibrary.book.application;

import com.elibrary.book.domain.Book;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.domain.NoLicenseException;
import com.elibrary.book.mapper.BookMapper;
import com.elibrary.common.domain.OptimisticLockConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BookFacade 单测：乐观锁重试语义（MP 会把递增后的 version 写回实体）
 *
 * @author link
 * @date 2026/10/20
 */
@ExtendWith(MockitoExtension.class)
class BookFacadeImplTest {

    @Mock BookMapper bookMapper;
    @InjectMocks BookFacadeImpl facade;

    /** 书不存在抛 BookNotFoundException（facade 层 404 语义） */
    @Test
    void missingBookThrows404() {
        when(bookMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> facade.getBook(9L)).isInstanceOf(BookNotFoundException.class);
    }

    /** 无冲突时首次 update 即成功，名额扣减只发一次 SQL */
    @Test
    void reserveLicenseSucceedsOnFirstAttempt() {
        Book book = book(1L, 3, 3);
        when(bookMapper.updateById(any(Book.class))).thenReturn(1);

        facade.reserveLicense(book);

        assertThat(book.getAvailableLicenses()).isEqualTo(2);
        verify(bookMapper).updateById(book);
    }

    /** 乐观锁冲突后当前读重载、重试一次成功（共两次 update） */
    @Test
    void optimisticLockConflictRetriesOnceAndSucceeds() {
        Book stale = book(1L, 3, 2);
        Book fresh = book(1L, 3, 3);
        when(bookMapper.updateById(any(Book.class))).thenReturn(0, 1); // 第一次冲突，第二次成功
        // 冲突重载必须当前读（RR 隔离下普通 select 读事务快照会拿到过期 version）
        when(bookMapper.selectByIdForUpdate(1L)).thenReturn(fresh);

        facade.reserveLicense(stale);

        verify(bookMapper, times(2)).updateById(any(Book.class));
    }

    /** 重试后仍冲突抛 OptimisticLockConflictException（CONCURRENT_MODIFICATION） */
    @Test
    void conflictAfterRetryThrowsConcurrentModification() {
        Book book = book(1L, 3, 3);
        when(bookMapper.updateById(any(Book.class))).thenReturn(0, 0);
        when(bookMapper.selectByIdForUpdate(1L)).thenReturn(book);

        assertThatThrownBy(() -> facade.reserveLicense(book))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    /** 名额耗尽在任何 update 之前抛出，不发多余 SQL */
    @Test
    void exhaustedLicensesThrowNoLicenseWithoutUpdate() {
        Book book = book(1L, 1, 0);
        assertThatThrownBy(() -> facade.reserveLicense(book)).isInstanceOf(NoLicenseException.class);
        // updateById 在 MP 3.5.12 有 T/Collection<T> 重载，需显式类型消除歧义
        verify(bookMapper, never()).updateById(any(Book.class));
    }

    /** 释放路径：先加载再更新，名额加回 */
    @Test
    void releaseLicenseReloadsAndUpdates() {
        Book book = book(1L, 3, 2);
        when(bookMapper.selectById(1L)).thenReturn(book); // releaseLicense 首次加载
        when(bookMapper.updateById(any(Book.class))).thenReturn(1);

        facade.releaseLicense(1L);

        verify(bookMapper).updateById(any(Book.class));
        assertThat(book.getAvailableLicenses()).isEqualTo(3);
    }

    private Book book(long id, int total, int available) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("重构");
        book.setTotalLicenses(total);
        book.setAvailableLicenses(available);
        book.setVersion(0);
        return book;
    }
}
