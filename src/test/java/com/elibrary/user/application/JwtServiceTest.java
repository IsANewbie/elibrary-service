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
    private static final String SECRET = "ZXRoaWJyYXJ5LWRldi1vbmx5LXNlY3JldC1rZXktMzIteWJ5dGVzLWxvbmc=";

    @Test
    void generatesAndParsesUserIdRoundTrip() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 24));
        String token = service.generate(42L);
        assertThat(service.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    void expiredTokenThrowsExpiredJwtException() {
        JwtService service = new JwtService(new JwtProperties(SECRET, -1));
        String token = service.generate(42L);
        assertThatThrownBy(() -> service.parseUserId(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenThrowsJwtException() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 24));
        String token = service.generate(42L) + "x";
        assertThatThrownBy(() -> service.parseUserId(token)).isInstanceOf(JwtException.class);
    }
}
