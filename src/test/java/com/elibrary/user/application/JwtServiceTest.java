package com.elibrary.user.application;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 服务单测（往返/过期/篡改）
 *
 * @author link
 * @date 2026/10/19
 */
class JwtServiceTest {

    // 与 application.yml 默认值同源，测试自带
    private static final String SECRET = "ZWxpYnJhcnktZGV2LW9ubHktc2VjcmV0LWtleS1ub3QtZm9yLXByb2R1Y3Rpb24=";

    /** 签发与解析往返还原 userId */
    @Test
    void generatesAndParsesUserIdRoundTrip() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 24));
        String token = service.generate(42L);
        assertThat(service.parseUserId(token)).isEqualTo(42L);
    }

    /** 过期 token 抛 ExpiredJwtException */
    @Test
    void expiredTokenThrowsExpiredJwtException() {
        JwtService service = new JwtService(new JwtProperties(SECRET, -1));
        String token = service.generate(42L);
        assertThatThrownBy(() -> service.parseUserId(token)).isInstanceOf(ExpiredJwtException.class);
    }

    /** 篡改签名抛 JwtException（HS256 完整性） */
    @Test
    void tamperedTokenThrowsJwtException() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 24));
        String token = service.generate(42L) + "x";
        assertThatThrownBy(() -> service.parseUserId(token)).isInstanceOf(JwtException.class);
    }

    /** 空字符串 token 抛 IllegalArgumentException（钉死 JwtAuthFilter 的捕获面） */
    @Test
    void blankTokenThrowsIllegalArgumentException() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 24));
        assertThatThrownBy(() -> service.parseUserId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 自签合法签名但 sub 非数字的 token 抛 NumberFormatException */
    @Test
    void nonNumericSubjectThrowsNumberFormatException() {
        // 自签合法签名但 sub 非数字的 token：NumberFormatException extends IllegalArgumentException
        javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                io.jsonwebtoken.io.Decoders.BASE64.decode(SECRET));
        String token = io.jsonwebtoken.Jwts.builder().subject("alice").signWith(key).compact();
        JwtService service = new JwtService(new JwtProperties(SECRET, 24));
        assertThatThrownBy(() -> service.parseUserId(token))
                .isInstanceOf(NumberFormatException.class);
    }
}
