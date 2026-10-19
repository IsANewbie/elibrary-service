package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

/**
 * 用户在借数量达到上限
 *
 * @author link
 * @date 2026/10/19
 */
public class LoanLimitException extends DomainException {
    public LoanLimitException(String message) { super(ErrorCode.LOAN_LIMIT_REACHED, message); }
}
