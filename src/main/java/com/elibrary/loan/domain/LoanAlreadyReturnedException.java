package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

/**
 * 借阅已被归还，禁止重复归还
 *
 * @author link
 * @date 2026/10/19
 */
public class LoanAlreadyReturnedException extends DomainException {
    public LoanAlreadyReturnedException(String message) { super(ErrorCode.LOAN_ALREADY_RETURNED, message); }
}
