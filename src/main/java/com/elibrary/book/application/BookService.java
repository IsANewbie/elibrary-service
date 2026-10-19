package com.elibrary.book.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elibrary.book.api.BookDetailResponse;
import com.elibrary.book.api.BookSummaryResponse;
import com.elibrary.book.domain.Book;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.mapper.BookMapper;
import com.elibrary.common.api.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 浏览/详情查询服务
 *
 * @author link
 * @date 2026/10/20
 */
@Service
public class BookService {

    private final BookMapper bookMapper;

    public BookService(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    public PageResponse<BookSummaryResponse> search(String keyword, String category, int page, int size) {
        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // title/author 任一命中；utf8mb4 CI collation 天然大小写不敏感
            wrapper.and(q -> q.like(Book::getTitle, keyword).or().like(Book::getAuthor, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Book::getCategory, category);
        }
        Page<Book> result = bookMapper.selectPage(Page.of(page, size), wrapper);
        List<BookSummaryResponse> content = result.getRecords().stream()
                .map(b -> new BookSummaryResponse(b.getId(), b.getTitle(), b.getAuthor(),
                        b.getCategory(), b.getAvailableLicenses()))
                .toList();
        return new PageResponse<>(content, result.getTotal(), page, size);
    }

    public BookDetailResponse getDetail(Long id, Long userId) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BookNotFoundException("书籍不存在: id=" + id);
        }
        boolean borrowedByYou = bookMapper.existsActiveBorrow(userId, id);
        return new BookDetailResponse(book.getId(), book.getTitle(), book.getAuthor(),
                book.getCategory(), book.getDescription(), book.getAvailableLicenses(), borrowedByYou);
    }
}
