package com.elibrary.user.api;

import com.elibrary.user.application.AuthService;
import com.elibrary.user.domain.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * login 端点切片测试：序列化、7807 错误契约、参数校验
 *
 * @author link
 * @date 2026/10/20
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    /** 登录成功返回 token */
    @Test
    void loginSuccessReturnsToken() throws Exception {
        when(authService.login("alice", "password123")).thenReturn("jwt-token");
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    /** 凭证错误返回 401 problem+json 且 errorCode 为 INVALID_CREDENTIALS */
    @Test
    void loginFailureReturns401ProblemJsonWithErrorCode() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new InvalidCredentialsException("用户名或密码错误"));
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    /** 缺 password 字段返回 400 且指明具体字段 */
    @Test
    void missingFieldReturns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.password").exists());
    }
}
