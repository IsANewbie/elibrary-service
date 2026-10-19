package com.elibrary.loan.api;

import com.elibrary.loan.application.LoanService;
import com.elibrary.loan.domain.Loan;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 借阅/归还/在借列表端点（当前用户一律从 principal 解出，无 userId 参数）
 *
 * @author link
 * @date 2026/10/20
 */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    public ResponseEntity<LoanResponse> borrow(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody BorrowRequest request) {
        Loan loan = loanService.borrow(userId, request.bookId());
        return ResponseEntity
                .created(URI.create("/api/loans/" + loan.getId()))
                .body(toResponse(loan));
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnLoan(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return loanService.returnLoan(userId, id);
    }

    @GetMapping
    public List<LoanResponse> currentLoans(@AuthenticationPrincipal Long userId) {
        return loanService.currentLoans(userId);
    }

    // 借阅刚创建时书名信息不在 Loan 聚合上（无冗余），与列表/归还视图的字段差异在 README 说明
    private LoanResponse toResponse(Loan loan) {
        return new LoanResponse(loan.getId(), loan.getBookId(), null, null,
                loan.getBorrowedAt(), loan.getDueAt(), loan.getReturnedAt(), false);
    }
}
