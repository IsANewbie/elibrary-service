package com.elibrary.loan.api;

import java.time.LocalDateTime;

/**
 * 借阅响应（列表/归还视图；借阅刚创建时书名字段为 null——Loan 聚合上无冗余）
 *
 * @param id         借阅记录 id
 * @param bookId     书籍 id
 * @param title      书名（JOIN 投影；借阅创建视图为 null）
 * @param author     作者（JOIN 投影；借阅创建视图为 null）
 * @param borrowedAt 借出时间（UTC）
 * @param dueAt      应还时间 = borrowedAt + 14 天
 * @param returnedAt 归还时间；null=在借
 * @param overdue    是否逾期（派生：在借 && now > dueAt，严格大于）
 * @author link
 * @date 2026/10/20
 */
public record LoanResponse(Long id, Long bookId, String title, String author,
                           LocalDateTime borrowedAt, LocalDateTime dueAt,
                           LocalDateTime returnedAt, boolean overdue) {
}
