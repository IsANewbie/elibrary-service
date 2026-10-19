package com.elibrary.security;

import com.elibrary.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 安全链集成测试
 *
 * @author link
 * @date 2026/10/19
 */
@AutoConfigureMockMvc
class SecurityIT extends AbstractIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void protectedEndpointWithoutTokenReturns401ProblemJson() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("缺少或无效的 Bearer token"));
    }

    @Test
    void invalidBearerTokenReturns401ProblemJson() throws Exception {
        mockMvc.perform(get("/api/books").header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void loginEndpointIsPermitted() throws Exception {
        // controller 未实现前是 404；T9 落地后此测试改期望 400（{} 缺字段 → VALIDATION_FAILED）
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
}
