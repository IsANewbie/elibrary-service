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
 *
 * @author link
 * @date 2026/10/19
 */
@Getter
@Setter
@TableName("loans")
public class Loan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long bookId;
    private LocalDateTime borrowedAt;
    private LocalDateTime dueAt;
    private LocalDateTime returnedAt;

    // MySQL 生成列（等效 partial unique index），只读
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
        return isActive() && now.isAfter(dueAt);
    }
}
