package com.elibrary.loan.mapper;

import com.elibrary.AbstractIT;
import com.elibrary.book.mapper.BookMapper;
import com.elibrary.loan.domain.Loan;
import com.elibrary.user.domain.User;
import com.elibrary.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JOIN 投影与计数查询真库冒烟：map-underscore 自动映射失败是静默 null，mock 测不到
 *
 * @author link
 * @date 2026/10/20
 */
class LoanMapperIT extends AbstractIT {

    @Autowired LoanMapper loanMapper;
    @Autowired UserMapper userMapper;
    @Autowired BookMapper bookMapper;

    /** JOIN 投影字段全部映射非空、计数/EXISTS 查询返回真值（列映射静默 null 只有真库能暴露） */
    @Test
    void joinProjectionAndCountQueriesMapCorrectly() {
        var user = new User();
        user.setUsername("projection_" + System.nanoTime());
        user.setPassword("x");
        userMapper.insert(user);
        var book = bookMapper.selectList(null).get(0);
        Loan loan = Loan.borrow(user.getId(), book.getId(), LocalDateTime.now(ZoneOffset.UTC));
        loanMapper.insert(loan);

        var views = loanMapper.selectActiveWithBook(user.getId());
        assertThat(views).hasSize(1);
        var view = views.get(0);
        assertThat(view.getBookId()).isEqualTo(book.getId());
        assertThat(view.getTitle()).isEqualTo(book.getTitle());
        assertThat(view.getAuthor()).isEqualTo(book.getAuthor());
        assertThat(view.getBorrowedAt()).isNotNull();
        assertThat(view.getDueAt()).isNotNull();
        assertThat(view.getReturnedAt()).isNull();

        assertThat(bookMapper.existsActiveBorrow(user.getId(), book.getId())).isTrue();
        assertThat(loanMapper.countActiveByUser(user.getId())).isEqualTo(1);
        assertThat(loanMapper.existsActiveByUserAndBook(user.getId(), book.getId())).isTrue();

        // 清理，避免污染其他测试
        loanMapper.deleteById(loan.getId());
        userMapper.deleteById(user.getId());
    }
}
