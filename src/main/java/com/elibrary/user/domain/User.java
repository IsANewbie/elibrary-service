package com.elibrary.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户实体，借阅归属方与行锁锚点，非账户体系
 *
 * @author link
 * @date 2026/10/19
 */
@Getter
@Setter
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;         // 主键
    private String username; // 用户名（唯一，utf8mb4 CI 大小写不敏感）
    private String password; // BCrypt 哈希（种子用户启动时注入）
}
