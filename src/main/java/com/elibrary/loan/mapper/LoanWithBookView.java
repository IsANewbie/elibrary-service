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
    private Long id;
    private Long bookId;
    private String title;
    private String author;
    private LocalDateTime borrowedAt;
    private LocalDateTime dueAt;
    private LocalDateTime returnedAt;
}
