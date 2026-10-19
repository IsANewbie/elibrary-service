package com.elibrary.book.api;

import com.elibrary.book.domain.Book;

/**
 * 写侧跨 feature 唯一通道：拆分时把实现换成 REST 客户端，LoanService 一行不改
 *
 * @author link
 * @date 2026/10/20
 */
public interface BookFacade {
    Book getBook(Long id);

    /** book 必须经 getBook/selectById 加载（version 非 null，乐观锁拦截器才生效） */
    void reserveLicense(Book book);

    void releaseLicense(Long bookId);
}
