package com.elibrary.loan.application;

import com.elibrary.book.api.BookFacade;
import com.elibrary.book.domain.Book;
import com.elibrary.loan.domain.Loan;
import com.elibrary.loan.domain.LoanDuplicateException;
import com.elibrary.loan.domain.LoanLimitException;
import com.elibrary.loan.domain.LoanPolicy;
import com.elibrary.loan.mapper.LoanMapper;
import com.elibrary.user.api.UserFacade;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 借阅/归还/在借列表应用服务：事务、锁顺序、编排（业务规则在聚合内）
 *
 * @author link
 * @date 2026/10/20
 */
@Service
public class LoanService {

    private final UserFacade userFacade;
    private final BookFacade bookFacade;
    private final LoanMapper loanMapper;

    public LoanService(UserFacade userFacade, BookFacade bookFacade, LoanMapper loanMapper) {
        this.userFacade = userFacade;
        this.bookFacade = bookFacade;
        this.loanMapper = loanMapper;
    }

    /**
     * 校验链顺序即并发设计（design_walkthrough §3）：
     * user 行锁 → 加载书 → 在借数检查 → 重复借检查 → 扣名额 → 插入。
     */
    @Transactional
    public Loan borrow(Long userId, Long bookId) {
        userFacade.lockById(userId);
        Book book = bookFacade.getBook(bookId);
        if (loanMapper.countActiveByUser(userId) >= LoanPolicy.MAX_ACTIVE_LOANS) {
            throw new LoanLimitException("在借已达上限: " + LoanPolicy.MAX_ACTIVE_LOANS);
        }
        if (loanMapper.existsActiveByUserAndBook(userId, bookId)) {
            throw new LoanDuplicateException("已借阅此书，不可重复借阅");
        }
        bookFacade.reserveLicense(book);
        Loan loan = Loan.borrow(userId, bookId, LocalDateTime.now(ZoneOffset.UTC));
        try {
            loanMapper.insert(loan);
        } catch (DuplicateKeyException e) {
            // 并发双击穿透应用检查时，生成列唯一索引兜底；仅翻译目标约束，其他冲突不吞并保留根因
            if (e.getMessage() != null && e.getMessage().contains("uq_loans_user_active_book")) {
                throw new LoanDuplicateException("重复借阅（唯一约束兜底命中）", e);
            }
            throw e;
        }
        return loan;
    }
}
