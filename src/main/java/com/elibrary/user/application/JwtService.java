package com.elibrary.user.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与解析（HS256，sub=userId）
 *
 * @author link
 * @date 2026/10/19
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final int expireHours;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.expireHours = properties.expireHours();
    }

    public String generate(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
