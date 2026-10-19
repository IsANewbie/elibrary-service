package com.elibrary.user.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体
 *
 * @author link
 * @date 2026/10/20
 * @param username 用户名
 * @param password 明文密码（传输层安全交给 HTTPS；库中只存 BCrypt 哈希）
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
