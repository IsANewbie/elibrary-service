package com.elibrary.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * 切片测试注入 userId principal（与 JwtAuthFilter 写入的形态一致）
 *
 * @author link
 * @date 2026/10/20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockUserId.Factory.class)
public @interface WithMockUserId {

    long value() default 1L;

    class Factory implements WithSecurityContextFactory<WithMockUserId> {
        @Override
        public SecurityContext createSecurityContext(WithMockUserId annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(annotation.value(), null, List.of()));
            return context;
        }
    }
}
