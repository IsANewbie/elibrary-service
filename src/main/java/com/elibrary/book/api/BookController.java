package com.elibrary.book.api;

import com.elibrary.book.application.BookService;
import com.elibrary.common.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 浏览/详情端点
 *
 * @author link
 * @date 2026/10/20
 */
@RestController
@Validated
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/api/books")
    public PageResponse<BookSummaryResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam @Min(1) int page,
            @RequestParam @Min(1) @Max(100) int size) {
        return bookService.search(keyword, category, page, size);
    }

    @GetMapping("/api/books/{id}")
    public BookDetailResponse detail(@PathVariable Long id,
                                     @AuthenticationPrincipal Long userId) {
        return bookService.getDetail(id, userId);
    }
}
