package com.elibrary.book.api;

/**
 * 书籍详情：比列表多 description 与用户视角的 borrowedByYou
 *
 * @author link
 * @date 2026/10/20
 */
public record BookDetailResponse(Long id, String title, String author, String category,
                                  String description, int availableLicenses, boolean borrowedByYou) {}
