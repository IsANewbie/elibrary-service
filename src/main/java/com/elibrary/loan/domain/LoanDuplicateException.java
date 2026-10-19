package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

/**
 * 同用户同书重复借阅
 *
 * @author link
 * @date 2026/10/19
 */
public class LoanDuplicateException extends DomainException {
    public LoanDuplicateException(String message) { super(ErrorCode.LOAN_DUPLICATE, message); }

    public LoanDuplicateException(String message, Throwable cause) { super(ErrorCode.LOAN_DUPLICATE, message, cause); }
}
