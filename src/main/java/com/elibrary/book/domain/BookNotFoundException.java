package com.elibrary.book.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

/**
 * 指定书籍不存在
 *
 * @author link
 * @date 2026/10/19
 */
public class BookNotFoundException extends DomainException {
    public BookNotFoundException(String message) { super(ErrorCode.BOOK_NOT_FOUND, message); }
}
