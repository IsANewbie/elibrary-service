package com.elibrary.common.api;

import java.util.List;

/**
 * 分页响应信封
 *
 * @author link
 * @date 2026/10/20
 */
public record PageResponse<T>(List<T> content, long total, int page, int size) {}
