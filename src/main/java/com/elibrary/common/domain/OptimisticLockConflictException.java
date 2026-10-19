package com.elibrary.common.domain;

// 乐观锁冲突不属于单一 feature，是跨聚合更新的通用事实，故放 common
/**
 * 乐观锁冲突，跨聚合更新的通用事实
 *
 * @author link
 * @date 2026/10/19
 */
public class OptimisticLockConflictException extends DomainException {
    public OptimisticLockConflictException(String message) { super(ErrorCode.CONCURRENT_MODIFICATION, message); }
}
