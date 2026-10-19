package com.elibrary.loan.domain;

import java.time.LocalDateTime;

/**
 * 借阅限额、借期与 overdue 谓词的唯一来源，防规则散落双写
 *
 * @author link
 * @date 2026/10/19
 */
// 数值集中一处，需求只给了单一数字，类别化配置见 README 演进节（YAGNI）
public final class LoanPolicy {
    public static final int MAX_ACTIVE_LOANS = 5;
    public static final int LOAN_DAYS = 14;

    /**
     * overdue 判定的唯一真相（严格大于）：active && now > dueAt。
     * Loan.isOverdue 与在借列表派生共用，防公式双写漂移。
     */
    public static boolean isOverdue(LocalDateTime dueAt, LocalDateTime now) {
        return now.isAfter(dueAt);
    }

    private LoanPolicy() {}
}
