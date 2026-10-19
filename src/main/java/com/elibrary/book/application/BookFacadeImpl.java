package com.elibrary.book.application;

import com.elibrary.book.api.BookFacade;
import com.elibrary.book.domain.Book;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.mapper.BookMapper;
import com.elibrary.common.domain.OptimisticLockConflictException;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * BookFacade 实现：乐观锁冲突在 facade 内重试，loan 侧不感知
 *
 * @author link
 * @date 2026/10/20
 */
@Service
public class BookFacadeImpl implements BookFacade {

    // 乐观锁假冲突（名额充足但 version 撞）重试一次
    private static final int MAX_ATTEMPTS = 2;

    private final BookMapper bookMapper;

    public BookFacadeImpl(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    @Override
    public Book getBook(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BookNotFoundException("书籍不存在: id=" + id);
        }
        return book;
    }

    @Override
    public void reserveLicense(Book book) {
        updateWithRetry(book, Book::borrow, "扣减借阅名额失败: book=" + book.getId());
    }

    @Override
    public void releaseLicense(Long bookId) {
        updateWithRetry(getBook(bookId), Book::release, "释放借阅名额失败: book=" + bookId);
    }

    private void updateWithRetry(Book book, Consumer<Book> action, String conflictMessage) {
        Book current = book;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            action.accept(current);
            if (bookMapper.updateById(current) == 1) {
                return;
            }
            // 冲突后 reload 新版本再试（MP 会把递增后的 version 写回实体，旧实体作废）
            current = getBook(book.getId());
        }
        throw new OptimisticLockConflictException(conflictMessage);
    }
}
