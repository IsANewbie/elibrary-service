package com.elibrary.loan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elibrary.loan.domain.Loan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    // 读侧跨表投影（声明的拆分债）：在借列表一条 SQL 解决 N+1
    @Select("""
            SELECT l.id, l.book_id, b.title, b.author, l.borrowed_at, l.due_at, l.returned_at
            FROM loans l JOIN books b ON l.book_id = b.id
            WHERE l.user_id = #{userId} AND l.returned_at IS NULL
            ORDER BY l.borrowed_at DESC
            """)
    java.util.List<LoanWithBookView> selectActiveWithBook(@Param("userId") Long userId);
}
