package com.elibrary.loan.api;

import com.elibrary.loan.application.LoanService;
import com.elibrary.loan.domain.Loan;
import com.elibrary.loan.domain.LoanNotFoundException;
import com.elibrary.security.WithMockUserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 借阅/归还/在借切片测试
 *
 * @author link
 * @date 2026/10/20
 */
@WebMvcTest(LoanController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoanControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LoanService loanService;

    @Test
    @WithMockUserId(1)
    void borrowReturns201WithLocation() throws Exception {
        Loan loan = Loan.borrow(1L, 2L, LocalDateTime.now(ZoneOffset.UTC));
        loan.setId(10L);
        when(loanService.borrow(1L, 2L)).thenReturn(loan);

        mockMvc.perform(post("/api/loans").contentType(APPLICATION_JSON)
                        .content("{\"bookId\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/loans/10"))
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUserId(1)
    void nullBookIdReturns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/loans").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.bookId").exists());
    }

    @Test
    @WithMockUserId(2)
    void foreignLoanReturns404() throws Exception {
        when(loanService.returnLoan(2L, 5L))
                .thenThrow(new LoanNotFoundException("借阅记录不存在: id=5"));
        mockMvc.perform(post("/api/loans/5/return"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_NOT_FOUND"));
    }

    @Test
    @WithMockUserId(1)
    void currentLoansReturns200Array() throws Exception {
        when(loanService.currentLoans(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
