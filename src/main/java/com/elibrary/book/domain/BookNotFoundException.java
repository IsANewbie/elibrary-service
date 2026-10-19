package com.elibrary.book.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class BookNotFoundException extends DomainException {
    public BookNotFoundException(String message) { super(ErrorCode.BOOK_NOT_FOUND, message); }
}
