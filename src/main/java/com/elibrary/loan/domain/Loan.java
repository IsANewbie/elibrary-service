package com.elibrary.loan.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 借阅聚合，状态由时间戳派生、归还状态机在聚合内
 * 字段 setter 仅为 ORM 映射与测试搭建保留，状态变更必须走 borrow/markReturned。
 *
 * @author link
 * @date 2026/10/19
 */
@Getter
@Setter
@TableName("loans")
public class Loan {

    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键
    private Long userId;                // 借阅用户 id（无外键，归属校验在应用层）
    private Long bookId;                // 书籍 id（无外键，归属校验在应用层）
    private LocalDateTime borrowedAt;   // 借出时间（应用层 UTC）
    private LocalDateTime dueAt;        // 应还时间 = borrowedAt + 14 天
    private LocalDateTime returnedAt;   // 归还时间；null=在借——全系统唯一持久化状态变化

    // MySQL 生成列（等效 partial unique index），只读：在借行=book_id/已归还=NULL
    @TableField(value = "active_book_id", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long activeBookId;

    public static Loan borrow(Long userId, Long bookId, LocalDateTime now) {
        Loan loan = new Loan();
        loan.userId = userId;
        loan.bookId = bookId;
        loan.borrowedAt = now;
        loan.dueAt = now.plusDays(LoanPolicy.LOAN_DAYS);
        return loan;
    }

    public void markReturned(LocalDateTime now) {
        if (returnedAt != null) {
            throw new LoanAlreadyReturnedException("该借阅已归还: loan=" + id);
        }
        returnedAt = now;
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    public boolean isOverdue(LocalDateTime now) {
        return isActive() && LoanPolicy.isOverdue(dueAt, now);
    }
}
