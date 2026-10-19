package com.elibrary.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elibrary.user.domain.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户表 Mapper，含 FOR UPDATE 悲观锁查询
 *
 * @author link
 * @date 2026/10/19
 */
public interface UserMapper extends BaseMapper<User> {

    // 锁显式写在 SQL：借阅链路的第一步，语义一目了然
    @Select("SELECT * FROM users WHERE id = #{id} FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);
}
