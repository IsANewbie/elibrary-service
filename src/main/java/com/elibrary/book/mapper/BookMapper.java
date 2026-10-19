package com.elibrary.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elibrary.book.domain.Book;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 书籍表 Mapper，含读侧跨表投影
 *
 * @author link
 * @date 2026/10/19
 */
public interface BookMapper extends BaseMapper<Book> {

    // 读侧跨表投影（声明的拆分债）：详情页 borrowedByYou
    @Select("SELECT EXISTS(SELECT 1 FROM loans WHERE book_id = #{bookId} AND user_id = #{userId} AND returned_at IS NULL)")
    boolean existsActiveBorrow(@Param("userId") Long userId, @Param("bookId") Long bookId);
}
