package com.elibrary.user.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体
 *
 * @author link
 * @date 2026/10/20
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
