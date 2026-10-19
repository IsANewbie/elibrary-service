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
 * @param secret      HS256 签名密钥（base64，≥32 字节）；生产必须经环境变量 JWT_SECRET 注入
 * @param expireHours token 有效时长（小时）
 */
@Validated
@ConfigurationProperties(prefix = "elibrary.jwt")
public record JwtProperties(@NotBlank String secret, @Positive int expireHours) {}
