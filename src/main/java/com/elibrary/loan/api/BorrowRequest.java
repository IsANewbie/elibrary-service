package com.elibrary.loan.api;

import jakarta.validation.constraints.NotNull;

/**
 * 借阅请求体
 *
 * @author link
 * @date 2026/10/20
 * @param bookId 要借的书籍 id（借阅者从 token 解出，不信任请求体）
 */
public record BorrowRequest(@NotNull Long bookId) {}
