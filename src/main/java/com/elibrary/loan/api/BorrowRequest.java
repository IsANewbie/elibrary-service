package com.elibrary.loan.api;

import jakarta.validation.constraints.NotNull;

/**
 * 借阅请求体
 *
 * @author link
 * @date 2026/10/20
 */
public record BorrowRequest(@NotNull Long bookId) {}
