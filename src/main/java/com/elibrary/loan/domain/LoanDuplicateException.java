package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class LoanDuplicateException extends DomainException {
    public LoanDuplicateException(String message) { super(ErrorCode.LOAN_DUPLICATE, message); }

    public LoanDuplicateException(String message, Throwable cause) { super(ErrorCode.LOAN_DUPLICATE, message, cause); }
}
