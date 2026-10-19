package com.elibrary.user.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置属性（secret 与过期时长）
 *
 * @author link
 * @date 2026/10/19
 */
@Validated
@ConfigurationProperties(prefix = "elibrary.jwt")
public record JwtProperties(@NotBlank String secret, @Positive int expireHours) {}
