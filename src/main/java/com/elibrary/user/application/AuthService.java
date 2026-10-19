package com.elibrary.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.elibrary.user.domain.InvalidCredentialsException;
import com.elibrary.user.domain.User;
import com.elibrary.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录认证：凭证校验 + JWT 签发
 *
 * @author link
 * @date 2026/10/20
 */
@Slf4j
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            // 用户不存在与密码错误同错误：防用户名枚举（日志区分两种情况仅供服务端排查）
            log.warn("登录失败: username={}, 原因={}", username, user == null ? "用户不存在" : "密码错误");
            throw new InvalidCredentialsException("用户名或密码错误");
        }
        log.info("登录成功: userId={}", user.getId());
        return jwtService.generate(user.getId());
    }
}
