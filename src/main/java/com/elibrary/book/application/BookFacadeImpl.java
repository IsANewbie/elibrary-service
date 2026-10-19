package com.elibrary.book.application;

import com.elibrary.book.api.BookFacade;
import com.elibrary.book.domain.Book;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.mapper.BookMapper;
import com.elibrary.common.domain.OptimisticLockConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * BookFacade 实现：乐观锁冲突在 facade 内重试，loan 侧不感知
 *
 * @author link
 * @date 2026/10/20
 */
@Slf4j
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
            // 重试是假冲突的常态处理（INFO）；高频出现则提示热点书，需关注
            log.info("乐观锁冲突重试: bookId={}, attempt={}/{}", book.getId(), attempt + 1, MAX_ATTEMPTS);
            // 冲突后必须当前读重载：RR 隔离下普通 select 读事务快照，拿到的仍是过期 version
            current = requireBook(bookMapper.selectByIdForUpdate(book.getId()));
        }
        throw new OptimisticLockConflictException(conflictMessage);
    }

    private Book requireBook(Book book) {
        if (book == null) {
            throw new com.elibrary.book.domain.BookNotFoundException("书籍不存在");
        }
        return book;
    }
}
