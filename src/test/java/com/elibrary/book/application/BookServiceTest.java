package com.elibrary.book.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elibrary.book.api.BookDetailResponse;
import com.elibrary.book.domain.Book;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.mapper.BookMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 浏览/详情服务单测：搜索映射与 borrowedByYou
 *
 * @author link
 * @date 2026/10/20
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock BookMapper bookMapper;
    @InjectMocks BookService bookService;

    @Test
    void detailIncludesBorrowedByYou() {
        Book book = book(1L, 3, 1);
        when(bookMapper.selectById(1L)).thenReturn(book);
        when(bookMapper.existsActiveBorrow(42L, 1L)).thenReturn(true);

        BookDetailResponse detail = bookService.getDetail(1L, 42L);

        assertThat(detail.borrowedByYou()).isTrue();
        assertThat(detail.availableLicenses()).isEqualTo(1);
    }

    @Test
    void missingDetailThrows404() {
        when(bookMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> bookService.getDetail(9L, 42L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void searchMapsPaginationAndResponse() {
        Page<Book> page = new Page<>(1, 10);
        page.setRecords(List.of(book(1L, 3, 2)));
        page.setTotal(1);
        when(bookMapper.selectPage(any(), any())).thenReturn(page);

        var response = bookService.search("重构", "TECHNOLOGY", 1, 10);

        assertThat(response.content()).hasSize(1);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.content().get(0).availableLicenses()).isEqualTo(2);
    }

    private Book book(long id, int total, int available) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("重构");
        book.setAuthor("Martin Fowler");
        book.setCategory("TECHNOLOGY");
        book.setTotalLicenses(total);
        book.setAvailableLicenses(available);
        return book;
    }
}
