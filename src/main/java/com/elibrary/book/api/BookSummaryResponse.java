package com.elibrary.book.api;

/**
 * 书籍列表项：浏览的目的是决定是否借阅，故带 availableLicenses
 *
 * @author link
 * @date 2026/10/20
 * @param id                书籍 id
 * @param title             书名
 * @param author            作者
 * @param category          类别
 * @param availableLicenses 当前可借名额（列表携带它就是为了让用户能决策是否借）
 */
public record BookSummaryResponse(Long id, String title, String author, String category,
                                   int availableLicenses) {}
