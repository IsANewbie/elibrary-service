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
                // 严格带 charset 才是真钉子：只有显式 setCharacterEncoding(UTF-8) 存在时头里才有 ;charset=（Mock 对 +json 有 UTF-8 特例）
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
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
        // login 端点免认证放行：{} 缺字段 → 400 VALIDATION_FAILED（穿透到 controller 而非 401）
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void everyResponseCarriesTraceId() throws Exception {
        // 用户报障凭 X-Trace-Id 即可 grep 全链路日志
        mockMvc.perform(get("/api/books"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void validTokenPassesFilterChainToController() throws Exception {
        // login → token → 穿过 JwtAuthFilter → controller：JWT 主线端到端
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).get("token").asText();
        mockMvc.perform(get("/api/books").param("page", "1").param("size", "3")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
