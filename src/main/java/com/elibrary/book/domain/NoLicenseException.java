package com.elibrary.book.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class NoLicenseException extends DomainException {
    public NoLicenseException(String message) { super(ErrorCode.BOOK_NO_LICENSE, message); }
}
