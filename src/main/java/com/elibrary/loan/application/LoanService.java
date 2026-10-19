package com.elibrary.loan.application;

import com.elibrary.book.api.BookFacade;
import com.elibrary.book.domain.Book;
import com.elibrary.loan.api.LoanResponse;
import com.elibrary.loan.domain.Loan;
import com.elibrary.loan.domain.LoanAlreadyReturnedException;
import com.elibrary.loan.domain.LoanDuplicateException;
import com.elibrary.loan.domain.LoanLimitException;
import com.elibrary.loan.domain.LoanNotFoundException;
import com.elibrary.loan.domain.LoanPolicy;
import com.elibrary.loan.mapper.LoanMapper;
import com.elibrary.user.api.UserFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 借阅/归还/在借列表应用服务：事务、锁顺序、编排（业务规则在聚合内）
 *
 * @author link
 * @date 2026/10/20
 */
@Slf4j
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
                // 兜底命中=应用检查被并发穿透的信号，排查竞态时的第一手证据
                log.warn("唯一索引兜底命中（应用检查被穿透）: userId={}, bookId={}", userId, bookId);
                throw new LoanDuplicateException("重复借阅（唯一约束兜底命中）", e);
            }
            throw e;
        }
        log.info("借阅成功: loanId={}, userId={}, bookId={}, dueAt={}",
                loan.getId(), userId, bookId, loan.getDueAt());
        return loan;
    }

    /**
     * 归还：归属校验（非本人 404 防探测）→ 条件更新状态机（并发双归还恰好一个命中）→ 释放名额。
     * 不取 user 锁只碰 Book：依赖方向 user→book 单向无环，无死锁。
     */
    @Transactional
    public LoanResponse returnLoan(Long userId, Long loanId) {
        Loan loan = loanMapper.selectById(loanId);
        if (loan == null || !loan.getUserId().equals(userId)) {
            throw new LoanNotFoundException("借阅记录不存在: id=" + loanId);
        }
        // 并发双归还：先碰状态机的一方命中，后者在此得 409（未到 release，名额不双放）
        if (loanMapper.markReturned(loanId, LocalDateTime.now(ZoneOffset.UTC)) == 0) {
            log.warn("重复归还被条件状态机拦截: userId={}, loanId={}", userId, loanId);
            throw new LoanAlreadyReturnedException("该借阅已归还: loan=" + loanId);
        }
        bookFacade.releaseLicense(loan.getBookId());
        Book book = bookFacade.getBook(loan.getBookId());
        log.info("归还成功: loanId={}, userId={}, bookId={}", loanId, userId, loan.getBookId());
        return new LoanResponse(loan.getId(), loan.getBookId(), book.getTitle(), book.getAuthor(),
                loan.getBorrowedAt(), loan.getDueAt(), LocalDateTime.now(ZoneOffset.UTC), false);
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> currentLoans(Long userId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return loanMapper.selectActiveWithBook(userId).stream()
                .map(v -> new LoanResponse(v.getId(), v.getBookId(), v.getTitle(), v.getAuthor(),
                        v.getBorrowedAt(), v.getDueAt(), v.getReturnedAt(),
                        // SQL 已过滤 returned_at IS NULL，active 隐式成立；时间比较走唯一谓词
                        LoanPolicy.isOverdue(v.getDueAt(), now)))
                .toList();
    }
}
