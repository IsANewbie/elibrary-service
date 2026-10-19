package com.elibrary.loan;

import com.elibrary.AbstractIT;
import com.elibrary.book.domain.Book;
import com.elibrary.book.mapper.BookMapper;
import com.elibrary.common.domain.DomainException;
import com.elibrary.loan.application.LoanService;
import com.elibrary.user.domain.User;
import com.elibrary.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 并发正确性集成测试：四竞态 + 兜底约束，真库验证锁策略
 *
 * @author link
 * @date 2026/10/20
 */
class LoanConcurrencyIT extends AbstractIT {

    @Autowired LoanService loanService;
    @Autowired BookMapper bookMapper;
    @Autowired UserMapper userMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private final AtomicInteger counter = new AtomicInteger();

    @BeforeEach
    void resetData() {
        counter.set(0); // 竞态测试用共享计数器取下标，跨测试必须复位
        jdbcTemplate.execute("DELETE FROM loans");
        jdbcTemplate.execute("UPDATE books SET available_licenses = total_licenses, version = 0");
    }

    @Test
    void raceALastLicenseGoesToExactlyOneBorrower() throws Exception {
        Long bookId = bookIdByTitle("代码整洁之道"); // 总共 1 个名额
        List<Long> users = createUsers(8);

        List<String> results = runConcurrent(8,
                () -> tryBorrow(users.get(counter.getAndIncrement()), bookId));

        // 绝不超卖：恰好一人成功
        assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
        assertThat(results.stream().filter("BOOK_NO_LICENSE"::equals).count()).isEqualTo(7);
        assertThat(bookMapper.selectById(bookId).getAvailableLicenses()).isZero();
    }

    @Test
    void raceCSameUserDoubleClickCreatesExactlyOneLoan() throws Exception {
        Long aliceId = userIdByUsername("alice");
        Long bookId = bookIdByTitle("领域驱动设计");

        List<String> results = runConcurrent(2, () -> tryBorrow(aliceId, bookId));

        assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
        assertThat(results).containsExactlyInAnyOrder("OK", "LOAN_DUPLICATE");
        assertThat(loanCount(aliceId, bookId)).isEqualTo(1);
    }

    @Test
    void raceDSixthConcurrentBorrowHitsLimit() throws Exception {
        Long aliceId = userIdByUsername("alice");
        List<Long> bookIds = List.of(
                bookIdByTitle("重构：改善既有代码的设计"),
                bookIdByTitle("领域驱动设计"),
                bookIdByTitle("代码整洁之道"),
                bookIdByTitle("三体"),
                bookIdByTitle("百年孤独"),
                bookIdByTitle("自然"));

        List<String> results = runConcurrent(6,
                () -> tryBorrow(aliceId, bookIds.get(counter.getAndIncrement())));

        assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(5);
        assertThat(results.stream().filter("LOAN_LIMIT_REACHED"::equals).count()).isEqualTo(1);
    }

    @Test
    void raceEConcurrentReturnOneSucceedsOneConflicts() throws Exception {
        Long aliceId = userIdByUsername("alice");
        Long bookId = bookIdByTitle("三体");
        loanService.borrow(aliceId, bookId);
        Long loanId = jdbcTemplate.queryForObject(
                "SELECT id FROM loans WHERE user_id=" + aliceId, Long.class);

        List<String> results = runConcurrent(2, () -> {
            try {
                loanService.returnLoan(aliceId, loanId);
                return "OK";
            } catch (DomainException e) {
                return e.getCode().name();
            }
        });

        // 名额不双放
        assertThat(results).containsExactlyInAnyOrder("OK", "LOAN_ALREADY_RETURNED");
        assertThat(bookMapper.selectById(bookId).getAvailableLicenses()).isEqualTo(5);
    }

    @Test
    void fallbackUniqueIndexBlocksDirectDuplicateInsert() {
        Long aliceId = userIdByUsername("alice");
        Long bookId = bookIdByTitle("三体");
        String insert = "INSERT INTO loans (user_id, book_id, borrowed_at, due_at) "
                + "VALUES (?, ?, NOW(6), DATE_ADD(NOW(6), INTERVAL 14 DAY))";
        jdbcTemplate.update(insert, aliceId, bookId);
        // user 行锁只串行化服务路径；约束本身要能独立拦住绕过服务的重复在借
        assertThatThrownBy(() -> jdbcTemplate.update(insert, aliceId, bookId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ---- helpers ----

    private String tryBorrow(Long userId, Long bookId) {
        try {
            loanService.borrow(userId, bookId);
            return "OK";
        } catch (DomainException e) {
            return e.getCode().name();
        }
    }

    private List<String> runConcurrent(int threads, Callable<String> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown(); // 所有线程同点起跑，最大化竞态窗口
            List<String> out = new ArrayList<>();
            for (Future<String> f : futures) {
                out.add(f.get(30, TimeUnit.SECONDS));
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private Long bookIdByTitle(String title) {
        return bookMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<Book>().eq(Book::getTitle, title)).getId();
    }

    private Long userIdByUsername(String username) {
        return userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<User>().eq(User::getUsername, username)).getId();
    }

    private List<Long> createUsers(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n).mapToObj(i -> {
            User user = new User();
            user.setUsername("race_" + System.nanoTime() + "_" + i);
            user.setPassword("x");
            userMapper.insert(user);
            return user.getId();
        }).toList();
    }

    private long loanCount(Long userId, Long bookId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loans WHERE user_id=" + userId + " AND book_id=" + bookId, Long.class);
    }
}
