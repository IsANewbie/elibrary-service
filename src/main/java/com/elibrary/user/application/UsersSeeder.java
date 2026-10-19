package com.elibrary.user.application;

import com.elibrary.user.domain.User;
import com.elibrary.user.mapper.UserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 种子用户注入：BCrypt 哈希无法静态生成，启动时编码后插入（不走 Flyway）
 *
 * @author link
 * @date 2026/10/20
 */
@Component
public class UsersSeeder implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UsersSeeder(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        userMapper.insert(user("alice"));
        userMapper.insert(user("bob"));
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password123"));
        return user;
    }
}
