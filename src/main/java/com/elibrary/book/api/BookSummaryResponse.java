package com.elibrary.book.api;

/**
 * 书籍列表项：浏览的目的是决定是否借阅，故带 availableLicenses
 *
 * @author link
 * @date 2026/10/20
 */
public record BookSummaryResponse(Long id, String title, String author, String category,
                                   int availableLicenses) {}
