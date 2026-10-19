package com.elibrary.loan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elibrary.loan.domain.Loan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 借阅表 Mapper，含在借统计与 JOIN 投影
 *
 * @author link
 * @date 2026/10/19
 */
public interface LoanMapper extends BaseMapper<Loan> {

    @Select("SELECT COUNT(*) FROM loans WHERE user_id = #{userId} AND returned_at IS NULL")
    long countActiveByUser(@Param("userId") Long userId);

    @Select("SELECT EXISTS(SELECT 1 FROM loans WHERE user_id = #{userId} AND book_id = #{bookId} AND returned_at IS NULL)")
    boolean existsActiveByUserAndBook(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 归还状态机的并发守卫：loans 无 version 列，用条件更新保证并发双归还恰好一个事务命中
     * （DB 级守卫防应用 bug，与生成列唯一索引同一哲学）
     */
    @Update("UPDATE loans SET returned_at = #{now} WHERE id = #{id} AND returned_at IS NULL")
    int markReturned(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 读侧跨表投影（声明的拆分债）：在借列表一条 SQL 解决 N+1
    @Select("""
            SELECT l.id, l.book_id, b.title, b.author, l.borrowed_at, l.due_at, l.returned_at
            FROM loans l JOIN books b ON l.book_id = b.id
            WHERE l.user_id = #{userId} AND l.returned_at IS NULL
            ORDER BY l.borrowed_at DESC, l.id DESC
            """)
    java.util.List<LoanWithBookView> selectActiveWithBook(@Param("userId") Long userId);
}
