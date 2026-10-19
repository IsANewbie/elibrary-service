package com.elibrary.loan.domain;

/**
 * 借阅规则数值集中处（在借上限与借期）
 *
 * @author link
 * @date 2026/10/19
 */
// 数值集中一处，需求只给了单一数字，类别化配置见 README 演进节（YAGNI）
public final class LoanPolicy {
    public static final int MAX_ACTIVE_LOANS = 5;
    public static final int LOAN_DAYS = 14;

    private LoanPolicy() {}
}
