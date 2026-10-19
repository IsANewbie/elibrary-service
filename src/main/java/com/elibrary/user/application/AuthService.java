package com.elibrary.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.elibrary.user.domain.InvalidCredentialsException;
import com.elibrary.user.domain.User;
import com.elibrary.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录认证：凭证校验 + JWT 签发
 *
 * @author link
 * @date 2026/10/20
 */
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
            // 用户不存在与密码错误同错误：防用户名枚举
            throw new InvalidCredentialsException("用户名或密码错误");
        }
        return jwtService.generate(user.getId());
    }
}
