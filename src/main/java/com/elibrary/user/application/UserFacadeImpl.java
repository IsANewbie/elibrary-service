package com.elibrary.user.application;

import com.elibrary.user.api.UserFacade;
import com.elibrary.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * UserFacade 实现：FOR UPDATE 悲观锁
 *
 * @author link
 * @date 2026/10/20
 */
@Service
public class UserFacadeImpl implements UserFacade {

    private final UserMapper userMapper;

    public UserFacadeImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void lockById(Long userId) {
        // 行不存在属于 token 指向已删用户的防御分支，不是业务规则
        if (userMapper.selectByIdForUpdate(userId) == null) {
            throw new IllegalStateException("用户不存在: " + userId);
        }
    }
}
