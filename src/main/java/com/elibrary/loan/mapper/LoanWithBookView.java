package com.elibrary.loan.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在借列表 JOIN 投影（mapper 层视图 POJO）
 *
 * @author link
 * @date 2026/10/19
 */
// MyBatis 对 record 构造器映射不完善，mapper 层投影允许 POJO；web 层 DTO 仍用 record
@Data
public class LoanWithBookView {
    private Long id;                   // 借阅记录 id（loans 表）
    private Long bookId;               // 书籍 id（loans 表）
    private String title;              // 书名（books 表 JOIN 列）
    private String author;             // 作者（books 表 JOIN 列）
    private LocalDateTime borrowedAt;  // 借出时间（loans 表）
    private LocalDateTime dueAt;       // 应还时间（loans 表）
    private LocalDateTime returnedAt;  // 恒为 null（查询过滤 returned_at IS NULL，保留字段对齐响应）
}
