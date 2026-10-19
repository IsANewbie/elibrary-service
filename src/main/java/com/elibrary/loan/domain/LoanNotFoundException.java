package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class LoanNotFoundException extends DomainException {
    public LoanNotFoundException(String message) { super(ErrorCode.LOAN_NOT_FOUND, message); }
}
