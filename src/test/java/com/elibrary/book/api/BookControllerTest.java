package com.elibrary.book.api;

import com.elibrary.book.application.BookService;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.common.api.PageResponse;
import com.elibrary.security.WithMockUserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 浏览/详情切片测试：分页校验 400 不钳位、principal 解析
 *
 * @author link
 * @date 2026/10/20
 */
@WebMvcTest(BookController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookService bookService;

    @Test
    void listReturns200WithPageEnvelope() throws Exception {
        when(bookService.search(null, null, 1, 10)).thenReturn(
                new PageResponse<>(List.of(), 0, 1, 10));
        mockMvc.perform(get("/api/books").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void invalidPageReturns400WithoutClamping() throws Exception {
        // 页码非法显式 400，不悄悄钳位：参数校验在系统边界做
        mockMvc.perform(get("/api/books").param("page", "0").param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUserId // principal 非 null，stub 的 userId=1 才能命中
    void missingDetailReturns404ProblemJson() throws Exception {
        when(bookService.getDetail(9L, 1L)).thenThrow(new BookNotFoundException("书籍不存在: id=9"));
        mockMvc.perform(get("/api/books/9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("BOOK_NOT_FOUND"));
    }

    @Test
    @WithMockUserId(7)
    void detailResolvesCurrentUserFromPrincipal() throws Exception {
        when(bookService.getDetail(1L, 7L)).thenReturn(new BookDetailResponse(
                1L, "重构", "Martin Fowler", "TECHNOLOGY", "desc", 2, false));
        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrowedByYou").value(false));
        verify(bookService).getDetail(1L, 7L);
    }
}
