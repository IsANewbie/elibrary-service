package com.elibrary.common.domain;

import lombok.Getter;

@Getter
public abstract class DomainException extends RuntimeException {
    private final ErrorCode code;

    protected DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
