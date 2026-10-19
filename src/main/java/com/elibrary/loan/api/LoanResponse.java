package com.elibrary.loan.api;

import java.time.LocalDateTime;

/**
 * 借阅响应（列表/归还视图；借阅刚创建时书名字段为 null——Loan 聚合上无冗余）
 *
 * @author link
 * @date 2026/10/20
 */
public record LoanResponse(Long id, Long bookId, String title, String author,
                           LocalDateTime borrowedAt, LocalDateTime dueAt,
                           LocalDateTime returnedAt, boolean overdue) {}
