package com.elibrary.user.api;

/**
 * 登录响应体：JWT token
 *
 * @author link
 * @date 2026/10/20
 * @param token JWT（HS256，sub=userId，24h 有效）；后续请求放 Authorization: Bearer 头
 */
public record LoginResponse(String token) {}
