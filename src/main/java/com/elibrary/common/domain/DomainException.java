package com.elibrary.common.domain;

import lombok.Getter;

/**
 * 领域异常抽象基类，携带稳定 ErrorCode 与可选 cause
 *
 * @author link
 * @date 2026/10/19
 */
@Getter
public abstract class DomainException extends RuntimeException {
    private final ErrorCode code;

    protected DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    // 保留根因：DB 约束兜底翻译等场景不能让原始异常蒸发
    protected DomainException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
