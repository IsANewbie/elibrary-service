package com.elibrary.book.api;

/**
 * 书籍详情：比列表多 description 与用户视角的 borrowedByYou
 *
 * @author link
 * @date 2026/10/20
 * @param id                书籍 id
 * @param title             书名
 * @param author            作者
 * @param category          类别（TECHNOLOGY/LITERATURE/JOURNAL）
 * @param description       简介（仅详情返回）
 * @param availableLicenses 当前可借名额
 * @param borrowedByYou     当前用户是否在借此书（用户视角，一行 JOIN 投影的代价）
 */
public record BookDetailResponse(Long id, String title, String author, String category,
                                  String description, int availableLicenses, boolean borrowedByYou) {}
