package com.elibrary.book.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

/**
 * 书籍可借名额耗尽
 *
 * @author link
 * @date 2026/10/19
 */
public class NoLicenseException extends DomainException {
    public NoLicenseException(String message) { super(ErrorCode.BOOK_NO_LICENSE, message); }
}
