package com.elibrary.book.mapper;

import com.elibrary.AbstractIT;
import com.elibrary.book.domain.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移与 Mapper 集成测试
 *
 * @author link
 * @date 2026/10/19
 */
class BookMapperIT extends AbstractIT {

    @Autowired
    BookMapper bookMapper;

    @Test
    void migrationAndSeedDataInPlace() {
        assertThat(bookMapper.selectCount(null)).isEqualTo(6);
    }

    @Test
    void keywordSearchIsCaseInsensitive() {
        // utf8mb4_0900_ai_ci 默认 CI collation：大写关键词命中混合大小写作者名，无需 lower()
        Long count = bookMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Book>()
                        .like(Book::getAuthor, "MARTIN FOWLER"));
        assertThat(count).isEqualTo(1);
    }

    @Test
    void paginationInterceptorReallyExecutes() {
        // PaginationInnerInterceptor 真跑一次：依赖缺失/配置问题在此暴露，不漏到运行时
        var page = bookMapper.selectPage(
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(1, 3), null);
        assertThat(page.getRecords()).hasSize(3);
        assertThat(page.getTotal()).isEqualTo(6);
    }
}
