package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

/**
 * 借阅记录不存在（含非本人归属的防探测语义）
 *
 * @author link
 * @date 2026/10/19
 */
public class LoanNotFoundException extends DomainException {
    public LoanNotFoundException(String message) { super(ErrorCode.LOAN_NOT_FOUND, message); }
}
