package com.elibrary.common.domain;

/**
 * 业务错误码枚举，全系统唯一错误标识；HTTP 映射集中在 common/api
 *
 * @author link
 * @date 2026/10/19
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    INVALID_CREDENTIALS,
    BOOK_NOT_FOUND,
    LOAN_NOT_FOUND,
    BOOK_NO_LICENSE,
    LOAN_LIMIT_REACHED,
    LOAN_DUPLICATE,
    LOAN_ALREADY_RETURNED,
    CONCURRENT_MODIFICATION
}
