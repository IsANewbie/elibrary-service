package com.elibrary.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性（secret 与过期时长）
 *
 * @author link
 * @date 2026/10/19
 */
@ConfigurationProperties(prefix = "elibrary.jwt")
public record JwtProperties(String secret, int expireHours) {}
