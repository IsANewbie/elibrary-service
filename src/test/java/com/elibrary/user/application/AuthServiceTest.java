package com.elibrary.user.application;

import com.elibrary.user.domain.InvalidCredentialsException;
import com.elibrary.user.domain.User;
import com.elibrary.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 登录认证服务单测：凭证校验、防用户名枚举
 *
 * @author link
 * @date 2026/10/20
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserMapper userMapper;
    @Spy BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    @Test
    void validCredentialsReturnToken() {
        User alice = user(1L, "alice", encoder.encode("password123"));
        when(userMapper.selectOne(any())).thenReturn(alice);
        when(jwtService.generate(1L)).thenReturn("jwt-token");

        assertThat(authService.login("alice", "password123")).isEqualTo("jwt-token");
    }

    @Test
    void wrongPasswordThrowsInvalidCredentials() {
        User alice = user(1L, "alice", encoder.encode("password123"));
        when(userMapper.selectOne(any())).thenReturn(alice);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unknownUserFailsSameAsWrongPassword() {
        // 用户不存在与密码错误同响应：防用户名枚举
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login("nobody", "x"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private User user(long id, String username, String password) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        return user;
    }
}
