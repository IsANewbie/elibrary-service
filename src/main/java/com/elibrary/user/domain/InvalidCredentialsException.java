package com.elibrary.user.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

/**
 * 登录凭证无效（用户不存在与密码错误同响应，防枚举）
 *
 * @author link
 * @date 2026/10/20
 */
public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException(String message) { super(ErrorCode.INVALID_CREDENTIALS, message); }
}
