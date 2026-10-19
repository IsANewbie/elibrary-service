package com.elibrary.common.api;

import java.util.List;

/**
 * 分页响应信封
 *
 * @author link
 * @date 2026/10/20
 * @param content 当前页数据
 * @param total   总记录数（供前端算总页数）
 * @param page    当前页码（从 1 起）
 * @param size    每页条数
 */
public record PageResponse<T>(List<T> content, long total, int page, int size) {}
