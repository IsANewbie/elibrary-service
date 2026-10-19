package com.elibrary.user.api;

/**
 * 写侧跨 feature 唯一通道（user 域）：借阅链路的悲观锁锚点
 *
 * @author link
 * @date 2026/10/20
 */
public interface UserFacade {
    /** 悲观锁锚点：借阅链路第一步，串行化同用户操作 */
    void lockById(Long userId);
}
