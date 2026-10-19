package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class LoanLimitException extends DomainException {
    public LoanLimitException(String message) { super(ErrorCode.LOAN_LIMIT_REACHED, message); }
}
