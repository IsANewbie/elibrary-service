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
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void loginEndpointIsPermitted() throws Exception {
        // controller 未实现前是 404；断言重点是"不是 401"（permitAll 生效）
        int status = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
    }
}
