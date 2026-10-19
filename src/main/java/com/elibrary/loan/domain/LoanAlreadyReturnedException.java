package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class LoanAlreadyReturnedException extends DomainException {
    public LoanAlreadyReturnedException(String message) { super(ErrorCode.LOAN_ALREADY_RETURNED, message); }
}
