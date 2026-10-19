# E-Library Service 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现电子图书馆后端（浏览/详情/借阅/归还/在借 + JWT login），Spring Boot + MyBatis-Plus + MySQL 8 + Flyway + Testcontainers。

**Architecture:** package-by-feature 单体（`book/ loan/ user/ common/`），充血领域模型，写侧跨 feature 只经 Facade（BookFacade/UserFacade），读侧 mapper JOIN 投影；借阅并发 = user 行悲观锁 + Book 乐观锁（@Version，重试一次）+ 生成列唯一索引/CHECK 兜底；状态由时间戳派生不落库；错误统一 RFC 7807 + errorCode。

**Tech Stack:** Java 21、Spring Boot 3.5.x（Web/Security/Validation）、MyBatis-Plus 3.5.x（spring-boot3-starter）、MySQL 8.4、Flyway、jjwt 0.12、Lombok、JUnit 5 + Mockito + @WebMvcTest + Testcontainers。版本策略：稳定优先不追新。

**设计依据：** `docs/requirements_analysis.md`、`docs/design_walkthrough.md`（v2 定稿）。借阅/异常逻辑有疑义先回读设计文档对应节。

**对设计文档的两处落地偏差（已论证）：**
1. 种子用户不走 Flyway（BCrypt 哈希无法静态生成，硬编码哈希不可维护）→ `UsersSeeder`（ApplicationRunner）启动时编码后插入；books 种子仍走 `V2__seed.sql`。
2. Spring Boot 选 3.5.x 而非 4.x：MyBatis-Plus/jjwt 生态在 3.5 验证最充分。

**验证命令约定：** `mvn test` = 领域/Service/Web 切片（无需 Docker）；`mvn verify` = 全部含 Testcontainers 集成测试（需 Docker）；单测：`mvn test -Dtest=LoanTest`。

**类头规范（所有任务的所有类，主代码与测试类都加）：**

```java
/**
 * <一行中文职责描述>
 *
 * @author link
 * @date <yyyy/MM/dd 文件创建日期>
 */
```
职责描述写类的存在理由而非复述类名；方法级注释仍遵循"只写为什么"。

---

### Task 0: git 仓库初始化

**Files:**
- Create: `.gitignore`

- [x] **Step 1: 初始化仓库并写 .gitignore**

```bash
git init -b main
```

```gitignore
target/
*.class
*.jar
.idea/
*.iml
.vscode/
.DS_Store
```

- [x] **Step 2: 提交既有文档**

```bash
git add .gitignore CLAUDE.md docs/
git commit -m "docs: 需求分析与设计推演 v2"
```

---

### Task 1: Maven 骨架

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/elibrary/ELibraryApplication.java`
- Create: `src/main/resources/application.yml`

- [ ] **Step 1: 写 pom.xml**（完整依赖一次到位，后续任务不再改）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.5</version>
    <relativePath/>
  </parent>
  <groupId>com.elibrary</groupId>
  <artifactId>elibrary-service</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <properties>
    <java.version>21</java.version>
    <mybatis-plus.version>3.5.12</mybatis-plus.version>
    <jjwt.version>0.12.6</jjwt.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
      <version>${mybatis-plus.version}</version>
    </dependency>
    <!-- PaginationInnerInterceptor 自 MP 3.5.9 拆分的独立 artifact，缺它分页插件编译失败 -->
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-jsqlparser</artifactId>
      <version>${mybatis-plus.version}</version>
    </dependency>
    <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-mysql</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>${jjwt.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-testcontainers</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>mysql</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <configuration>
          <excludes>
            <exclude>
              <groupId>org.projectlombok</groupId>
              <artifactId>lombok</artifactId>
            </exclude>
          </excludes>
        </configuration>
      </plugin>
      <!-- 集成测试（*IT.java）走 failsafe，mvn test 不需要 Docker -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <excludes>
            <exclude>**/*IT.java</exclude>
          </excludes>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-failsafe-plugin</artifactId>
        <executions>
          <execution>
            <goals>
              <goal>integration-test</goal>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 启动类**（`@MapperScan` 集中扫 mapper，feature 包不再重复配置）

```java
package com.elibrary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// lazyInitialization：@WebMvcTest 切片上下文没有 SqlSessionFactory，eager mapper 会让切片全部启动失败
@MapperScan(value = "com.elibrary.**.mapper", lazyInitialization = "true")
public class ELibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ELibraryApplication.class, args);
    }
}
```

- [ ] **Step 3: application.yml**

```yaml
spring:
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://localhost:3306/elibrary?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC}
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  flyway:
    enabled: true

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true

elibrary:
  jwt:
    # 仅为本地默认值，生产经环境变量注入
    secret: ${JWT_SECRET:ZXRoaWJyYXJ5LWRldi1vbmx5LXNlY3JldC1rZXktMzIteWJ5dGVzLWxvbmc=}
    expire-hours: 24

logging:
  level:
    com.elibrary: INFO
```

- [ ] **Step 4: 验证编译通过**

```bash
mvn compile
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/
git commit -m "feat: Maven 骨架与全局配置"
```

---

### Task 2: 领域异常体系

**Files:**
- Create: `src/main/java/com/elibrary/common/domain/ErrorCode.java`
- Create: `src/main/java/com/elibrary/common/domain/DomainException.java`
- Create: `src/main/java/com/elibrary/book/domain/BookNotFoundException.java`
- Create: `src/main/java/com/elibrary/book/domain/NoLicenseException.java`
- Create: `src/main/java/com/elibrary/loan/domain/LoanNotFoundException.java`
- Create: `src/main/java/com/elibrary/loan/domain/LoanLimitException.java`
- Create: `src/main/java/com/elibrary/loan/domain/LoanDuplicateException.java`
- Create: `src/main/java/com/elibrary/loan/domain/LoanAlreadyReturnedException.java`
- Create: `src/main/java/com/elibrary/common/domain/OptimisticLockConflictException.java`
- Test: `src/test/java/com/elibrary/common/domain/DomainExceptionTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.elibrary.common.domain;

import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.domain.NoLicenseException;
import com.elibrary.loan.domain.LoanAlreadyReturnedException;
import com.elibrary.loan.domain.LoanDuplicateException;
import com.elibrary.loan.domain.LoanLimitException;
import com.elibrary.loan.domain.LoanNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionTest {

    @Test
    void 异常携带稳定errorCode() {
        var ex = new LoanNotFoundForTest("loan 不存在");
        assertThat(ex.getCode()).isEqualTo(ErrorCode.LOAN_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("loan 不存在");
    }

    @Test
    void cause链构造器保留根因() {
        var root = new IllegalStateException("uq_loans_user_active_book");
        var ex = new CauseForTest("重复借阅", root);
        assertThat(ex.getCause()).isSameAs(root);
    }

    // 构造器绑定哪个 code 编译器不查，贴错则 HTTP 状态与 errorCode 双错，全覆盖兜底
    @ParameterizedTest
    @MethodSource("subclassBindings")
    void 子类绑定正确的ErrorCode(DomainException ex, ErrorCode expected) {
        assertThat(ex.getCode()).isEqualTo(expected);
    }

    static Stream<Arguments> subclassBindings() {
        return Stream.of(
                Arguments.of(new BookNotFoundException("x"), ErrorCode.BOOK_NOT_FOUND),
                Arguments.of(new NoLicenseException("x"), ErrorCode.BOOK_NO_LICENSE),
                Arguments.of(new LoanNotFoundException("x"), ErrorCode.LOAN_NOT_FOUND),
                Arguments.of(new LoanLimitException("x"), ErrorCode.LOAN_LIMIT_REACHED),
                Arguments.of(new LoanDuplicateException("x"), ErrorCode.LOAN_DUPLICATE),
                Arguments.of(new LoanAlreadyReturnedException("x"), ErrorCode.LOAN_ALREADY_RETURNED),
                Arguments.of(new OptimisticLockConflictException("x"), ErrorCode.CONCURRENT_MODIFICATION));
    }

    static class LoanNotFoundForTest extends DomainException {
        LoanNotFoundForTest(String message) { super(ErrorCode.LOAN_NOT_FOUND, message); }
    }

    static class CauseForTest extends DomainException {
        CauseForTest(String message, Throwable cause) { super(ErrorCode.LOAN_DUPLICATE, message, cause); }
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=DomainExceptionTest
```

Expected: COMPILATION ERROR（ErrorCode/DomainException 不存在）

- [ ] **Step 3: 实现**（ErrorCode 只是稳定字符串，HTTP 映射集中在 common/api，领域层不感知 HTTP）

```java
package com.elibrary.common.domain;

public enum ErrorCode {
    VALIDATION_FAILED,
    INVALID_CREDENTIALS,
    BOOK_NOT_FOUND,
    LOAN_NOT_FOUND,
    BOOK_NO_LICENSE,
    LOAN_LIMIT_REACHED,
    LOAN_DUPLICATE,
    LOAN_ALREADY_RETURNED,
    CONCURRENT_MODIFICATION
}
```

```java
package com.elibrary.common.domain;

import lombok.Getter;

@Getter
public abstract class DomainException extends RuntimeException {
    private final ErrorCode code;

    protected DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    // 保留根因：DB 约束兜底翻译等场景不能让原始异常蒸发
    protected DomainException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
```

六个子类（每个 4 行，模式相同，message 由抛出点给出业务上下文）：

```java
package com.elibrary.book.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class BookNotFoundException extends DomainException {
    public BookNotFoundException(String message) { super(ErrorCode.BOOK_NOT_FOUND, message); }
}
```

```java
package com.elibrary.book.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class NoLicenseException extends DomainException {
    public NoLicenseException(String message) { super(ErrorCode.BOOK_NO_LICENSE, message); }
}
```

```java
package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class LoanNotFoundException extends DomainException {
    public LoanNotFoundException(String message) { super(ErrorCode.LOAN_NOT_FOUND, message); }
}
```

```java
package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class LoanLimitException extends DomainException {
    public LoanLimitException(String message) { super(ErrorCode.LOAN_LIMIT_REACHED, message); }
}
```

```java
package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class LoanDuplicateException extends DomainException {
    public LoanDuplicateException(String message) { super(ErrorCode.LOAN_DUPLICATE, message); }

    // 供唯一索引兜底翻译链根因
    public LoanDuplicateException(String message, Throwable cause) { super(ErrorCode.LOAN_DUPLICATE, message, cause); }
}
```

```java
package com.elibrary.loan.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class LoanAlreadyReturnedException extends DomainException {
    public LoanAlreadyReturnedException(String message) { super(ErrorCode.LOAN_ALREADY_RETURNED, message); }
}
```

```java
package com.elibrary.common.domain;

// 乐观锁冲突不属于单一 feature，是跨聚合更新的通用事实，故放 common
public class OptimisticLockConflictException extends DomainException {
    public OptimisticLockConflictException(String message) { super(ErrorCode.CONCURRENT_MODIFICATION, message); }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
mvn test -Dtest=DomainExceptionTest
```

Expected: `Tests run: 9, Failures: 0`（2 个普通测试 + 7 个参数化绑定）

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: 领域异常体系与稳定 errorCode"
```

---

### Task 3: 领域模型（Book / Loan / LoanPolicy）

**Files:**
- Create: `src/main/java/com/elibrary/book/domain/Book.java`
- Create: `src/main/java/com/elibrary/loan/domain/Loan.java`
- Create: `src/main/java/com/elibrary/loan/domain/LoanPolicy.java`
- Test: `src/test/java/com/elibrary/book/domain/BookTest.java`
- Test: `src/test/java/com/elibrary/loan/domain/LoanTest.java`

- [ ] **Step 1: 写 Book 失败测试**

```java
package com.elibrary.book.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTest {

    @Test
    void 借阅扣减一个名额() {
        Book book = book(3);
        book.borrow();
        assertThat(book.getAvailableLicenses()).isEqualTo(2);
    }

    @Test
    void 名额为零时借阅抛NoLicense() {
        Book book = book(0);
        assertThatThrownBy(book::borrow).isInstanceOf(NoLicenseException.class);
    }

    @Test
    void 归还释放一个名额() {
        Book book = book(3, 1);
        book.release();
        assertThat(book.getAvailableLicenses()).isEqualTo(2);
    }

    @Test
    void 名额已满时释放是程序错误() {
        // 双放名额是调用方 bug（loan 状态机已挡），不是业务分支，故用 IllegalStateException 而非领域异常
        Book book = book(3, 3);
        assertThatThrownBy(book::release).isInstanceOf(IllegalStateException.class);
    }

    private Book book(int total) { return book(total, total); }

    private Book book(int total, int available) {
        Book book = new Book();
        book.setTitle("重构");
        book.setAuthor("Martin Fowler");
        book.setCategory("TECHNOLOGY");
        book.setTotalLicenses(total);
        book.setAvailableLicenses(available);
        return book;
    }
}
```

- [ ] **Step 2: 写 Loan 失败测试**

```java
package com.elibrary.loan.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Test
    void 借期固定14天() {
        Loan loan = Loan.borrow(1L, 2L, now);
        assertThat(loan.getDueAt()).isEqualTo(now.plusDays(14));
        assertThat(loan.getBorrowedAt()).isEqualTo(now);
    }

    @Test
    void 未归还即在借() {
        Loan loan = Loan.borrow(1L, 2L, now);
        assertThat(loan.isActive()).isTrue();
        assertThat(loan.isOverdue(now.plusDays(13))).isFalse();
        assertThat(loan.isOverdue(now.plusDays(15))).isTrue();
    }

    @Test
    void 归还后不再在借也不逾期() {
        Loan loan = Loan.borrow(1L, 2L, now);
        loan.markReturned(now.plusDays(3));
        assertThat(loan.isActive()).isFalse();
        assertThat(loan.isOverdue(now.plusDays(20))).isFalse();
    }

    @Test
    void 重复归还抛领域异常() {
        Loan loan = Loan.borrow(1L, 2L, now);
        loan.markReturned(now.plusDays(3));
        assertThatThrownBy(() -> loan.markReturned(now.plusDays(4)))
                .isInstanceOf(LoanAlreadyReturnedException.class);
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
mvn test -Dtest='BookTest,LoanTest'
```

Expected: COMPILATION ERROR

- [ ] **Step 4: 实现 LoanPolicy**

```java
package com.elibrary.loan.domain;

// 数值集中一处，需求只给了单一数字，类别化配置见 README 演进节（YAGNI）
public final class LoanPolicy {
    public static final int MAX_ACTIVE_LOANS = 5;
    public static final int LOAN_DAYS = 14;

    private LoanPolicy() {}
}
```

- [ ] **Step 5: 实现 Book**（规则在聚合内，MP 仅做映射；@Version 配合 OptimisticLockerInnerInterceptor）

```java
package com.elibrary.book.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("books")
public class Book {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String author;
    private String category;
    private String description;
    private Integer totalLicenses;
    private Integer availableLicenses;

    @Version
    private Integer version;

    public void borrow() {
        if (availableLicenses == null || availableLicenses <= 0) {
            throw new NoLicenseException("可借名额已耗尽: " + title);
        }
        availableLicenses--;
    }

    public void release() {
        if (availableLicenses >= totalLicenses) {
            // 双放是调用方 bug（loan 状态机已挡重复归还），用程序错误而非业务异常
            throw new IllegalStateException("名额已满，禁止超额释放: " + title);
        }
        availableLicenses++;
    }
}
```

- [ ] **Step 6: 实现 Loan**（activeBookId 是 MySQL 生成列，JPA/MP 都禁止写它）

```java
package com.elibrary.loan.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("loans")
public class Loan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long bookId;
    private LocalDateTime borrowedAt;
    private LocalDateTime dueAt;
    private LocalDateTime returnedAt;

    // MySQL 生成列（等效 partial unique index），只读
    @TableField(value = "active_book_id", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long activeBookId;

    public static Loan borrow(Long userId, Long bookId, LocalDateTime now) {
        Loan loan = new Loan();
        loan.userId = userId;
        loan.bookId = bookId;
        loan.borrowedAt = now;
        loan.dueAt = now.plusDays(LoanPolicy.LOAN_DAYS);
        return loan;
    }

    public void markReturned(LocalDateTime now) {
        if (returnedAt != null) {
            throw new LoanAlreadyReturnedException("该借阅已归还: loan=" + id);
        }
        returnedAt = now;
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    public boolean isOverdue(LocalDateTime now) {
        return isActive() && now.isAfter(dueAt);
    }
}
```

- [ ] **Step 7: 运行确认通过**

```bash
mvn test -Dtest='BookTest,LoanTest'
```

Expected: `Tests run: 8, Failures: 0`

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "feat: Book/Loan 充血领域模型与借阅规则"
```

---

### Task 4: Flyway schema 与种子数据

**Files:**
- Create: `src/main/resources/db/migration/V1__init.sql`
- Create: `src/main/resources/db/migration/V2__seed_books.sql`

- [ ] **Step 1: 写 V1__init.sql**（DDL 与 design_walkthrough §10 一致；不加外键——为拆分留路，归属校验在应用层）

```sql
CREATE TABLE users (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64)  NOT NULL,
    password VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE books (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    title              VARCHAR(255) NOT NULL,
    author             VARCHAR(128) NOT NULL,
    category           VARCHAR(64)  NOT NULL,
    description        TEXT,
    total_licenses     INT NOT NULL,
    available_licenses INT NOT NULL,
    version            INT NOT NULL DEFAULT 0,
    CHECK (available_licenses >= 0 AND available_licenses <= total_licenses),
    KEY idx_books_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE loans (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    book_id        BIGINT NOT NULL,
    borrowed_at    DATETIME(6) NOT NULL,
    due_at         DATETIME(6) NOT NULL,
    returned_at    DATETIME(6) NULL,
    -- 等效 partial unique index：在借行 (user_id, book_id) 唯一，已归还行 active_book_id 为 NULL 不受唯一约束
    active_book_id BIGINT GENERATED ALWAYS AS (IF(returned_at IS NULL, book_id, NULL)) STORED,
    UNIQUE KEY uq_loans_user_active_book (user_id, active_book_id),
    KEY idx_loans_user_active (user_id, returned_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

- [ ] **Step 2: 写 V2__seed_books.sql**（含 licenses=1 的书，供并发测试用）

```sql
INSERT INTO books (title, author, category, description, total_licenses, available_licenses) VALUES
('重构：改善既有代码的设计', 'Martin Fowler', 'TECHNOLOGY', '软件工程经典，讲解如何在既有代码上持续改进设计。', 3, 3),
('领域驱动设计', 'Eric Evans', 'TECHNOLOGY', '以领域模型为核心的软件设计方法论。', 2, 2),
('代码整洁之道', 'Robert C. Martin', 'TECHNOLOGY', '如何写出干净、可维护的代码。', 1, 1),
('三体', '刘慈欣', 'LITERATURE', '地球文明与三体文明的宇宙史诗。', 5, 5),
('百年孤独', '加西亚·马尔克斯', 'LITERATURE', '布恩迪亚家族七代人的传奇故事。', 2, 2),
('自然', 'Nature 编辑部', 'JOURNAL', '国际权威综合性科学期刊。', 10, 10);
```

- [ ] **Step 3: Commit**（迁移是否正确由 Task 5 的集成测试验证）

```bash
git add src/main/resources/db/
git commit -m "feat: Flyway schema（生成列唯一约束/CHECK 兜底）与书籍种子数据"
```

---

### Task 5: MyBatis-Plus 配置 + Book/User/Loan Mapper + 集成测试基座

**Files:**
- Create: `src/main/java/com/elibrary/common/MybatisPlusConfig.java`
- Create: `src/main/java/com/elibrary/book/mapper/BookMapper.java`
- Create: `src/main/java/com/elibrary/user/domain/User.java`
- Create: `src/main/java/com/elibrary/user/mapper/UserMapper.java`
- Create: `src/main/java/com/elibrary/loan/mapper/LoanMapper.java`
- Create: `src/main/java/com/elibrary/loan/mapper/LoanWithBookView.java`
- Test: `src/test/java/com/elibrary/AbstractIT.java`
- Test: `src/test/java/com/elibrary/book/mapper/BookMapperIT.java`

- [ ] **Step 1: MybatisPlusConfig**（乐观锁 + 分页两个内置拦截器）

```java
package com.elibrary.common;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
```

- [ ] **Step 2: User 实体 + 三个 Mapper**

```java
package com.elibrary.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
}
```

```java
package com.elibrary.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elibrary.user.domain.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {

    // 锁显式写在 SQL：借阅链路的第一步，语义一目了然
    @Select("SELECT * FROM users WHERE id = #{id} FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);
}
```

```java
package com.elibrary.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elibrary.book.domain.Book;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BookMapper extends BaseMapper<Book> {

    // 读侧跨表投影（声明的拆分债）：详情页 borrowedByYou
    @Select("SELECT EXISTS(SELECT 1 FROM loans WHERE book_id = #{bookId} AND user_id = #{userId} AND returned_at IS NULL)")
    boolean existsActiveBorrow(@Param("bookId") Long bookId, @Param("userId") Long userId);
}
```

```java
package com.elibrary.loan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elibrary.loan.domain.Loan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
```

```java
package com.elibrary.loan.mapper;

import lombok.Data;

import java.time.LocalDateTime;

// MyBatis 对 record 构造器映射不完善，mapper 层投影允许 POJO；web 层 DTO 仍用 record
@Data
public class LoanWithBookView {
    private Long id;
    private Long bookId;
    private String title;
    private String author;
    private LocalDateTime borrowedAt;
    private LocalDateTime dueAt;
    private LocalDateTime returnedAt;
}
```

- [ ] **Step 3: 集成测试基座**（Testcontainers + @ServiceConnection，真 MySQL 验证锁与约束）

```java
package com.elibrary;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class AbstractIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
}
```

- [ ] **Step 4: 写迁移/种子集成测试**

```java
package com.elibrary.book.mapper;

import com.elibrary.AbstractIT;
import com.elibrary.book.domain.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class BookMapperIT extends AbstractIT {

    @Autowired
    BookMapper bookMapper;

    @Test
    void 迁移与种子数据就位() {
        assertThat(bookMapper.selectCount(null)).isEqualTo(6);
    }

    @Test
    void 关键字大小写不敏感匹配() {
        // utf8mb4_0900_ai_ci 默认 CI collation：大写关键词命中混合大小写作者名，无需 lower()
        Long count = bookMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Book>()
                        .like(Book::getAuthor, "MARTIN FOWLER"));
        assertThat(count).isEqualTo(1);
    }

    @Test
    void 分页插件真实执行() {
        // PaginationInnerInterceptor 真跑一次：依赖缺失/配置问题在此暴露，不漏到运行时
        var page = bookMapper.selectPage(
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(1, 3), null);
        assertThat(page.getRecords()).hasSize(3);
        assertThat(page.getTotal()).isEqualTo(6);
    }
}
```

- [ ] **Step 5: 运行集成测试**（需要 Docker）

```bash
mvn verify -Dtest=SkipThis -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BookMapperIT
```

Expected: `Tests run: 3, Failures: 0`（迁移成功 + 种子 6 本 + CI collation 生效 + 分页插件真实执行）

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: MP 拦截器与 Mapper 层（FOR UPDATE/JOIN 投影）+ Testcontainers 基座"
```

---

### Task 6: JwtService（纯 TDD，无 Spring 上下文）

**Files:**
- Create: `src/main/java/com/elibrary/user/application/JwtProperties.java`
- Create: `src/main/java/com/elibrary/user/application/JwtService.java`
- Modify: `src/main/java/com/elibrary/ELibraryApplication.java`（加 `@ConfigurationPropertiesScan`）
- Test: `src/test/java/com/elibrary/user/application/JwtServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.elibrary.user.application;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // 与 application.yml 默认值同源，测试自带
    private static final String SECRET = "ZXRoaWJyYXJ5LWRldi1vbmx5LXNlY3JldC1rZXktMzIteWJ5dGVzLWxvbmc=";

    @Test
    void 签发并解析回userId() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 24));
        String token = service.generate(42L);
        assertThat(service.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    void 过期token抛ExpiredJwtException() {
        JwtService service = new JwtService(new JwtProperties(SECRET, -1));
        String token = service.generate(42L);
        assertThatThrownBy(() -> service.parseUserId(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 篡改token抛JwtException() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 24));
        String token = service.generate(42L) + "x";
        assertThatThrownBy(() -> service.parseUserId(token)).isInstanceOf(JwtException.class);
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=JwtServiceTest
```

Expected: COMPILATION ERROR

- [ ] **Step 3: 实现**

```java
package com.elibrary.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elibrary.jwt")
public record JwtProperties(String secret, int expireHours) {}
```

```java
package com.elibrary.user.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final int expireHours;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.expireHours = properties.expireHours();
    }

    public String generate(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
```

`ELibraryApplication` 追加注解：

```java
@SpringBootApplication
@MapperScan(value = "com.elibrary.**.mapper", lazyInitialization = "true")
@ConfigurationPropertiesScan
public class ELibraryApplication { /* 原样 */ }
```

- [ ] **Step 4: 运行确认通过**

```bash
mvn test -Dtest=JwtServiceTest
```

Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: JWT 签发与解析服务"
```

---

### Task 7: Security 配置 + JwtAuthFilter + 401 problem+json

**Files:**
- Create: `src/main/java/com/elibrary/common/security/JwtAuthFilter.java`
- Create: `src/main/java/com/elibrary/common/security/RestAuthenticationEntryPoint.java`
- Create: `src/main/java/com/elibrary/common/security/SecurityConfig.java`
- Test: `src/test/java/com/elibrary/security/SecurityIT.java`

- [ ] **Step 1: JwtAuthFilter**（不注册为 @Component，避免 servlet 容器重复注册；无效 token 不在过滤器 401，统一留给 entry point）

```java
package com.elibrary.common.security;

import com.elibrary.user.application.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Long userId = jwtService.parseUserId(header.substring(7));
                // principal 直接放 userId：业务层零转换
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            } catch (JwtException | IllegalArgumentException ignored) {
                // 解析失败保持未认证，由 entry point 统一 401
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: RestAuthenticationEntryPoint**

```java
package com.elibrary.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "缺少或无效的 Bearer token");
        problem.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
```

- [ ] **Step 3: SecurityConfig**

```java
package com.elibrary.common.security;

import com.elibrary.user.application.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                           RestAuthenticationEntryPoint entryPoint) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/login").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 4: 写集成测试**

```java
package com.elibrary.security;

import com.elibrary.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class SecurityIT extends AbstractIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 无token访问受保护接口返回401_problem_json() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void login端点免认证放行() throws Exception {
        // controller 未实现前是 404；断言重点是"不是 401"（permitAll 生效）
        int status = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
    }
}
```

- [ ] **Step 5: 运行**（需 Docker）

```bash
mvn verify -Dtest=SkipThis -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=SecurityIT
```

Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: 无状态 JWT 安全链与 problem+json 401"
```

---

### Task 8: 全局异常映射（RFC 7807 集中一处）

**Files:**
- Create: `src/main/java/com/elibrary/common/api/GlobalExceptionHandler.java`
- Test: `src/test/java/com/elibrary/common/api/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 写失败测试**（handler 是纯类，直接单元测，不起 Spring）

```java
package com.elibrary.common.api;

import com.elibrary.book.domain.NoLicenseException;
import com.elibrary.loan.domain.LoanNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodParameter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 领域异常按errorCode映射状态码() {
        ProblemDetail pd = handler.handleDomain(new NoLicenseException("名额耗尽"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pd.getProperty("errorCode")).isEqualTo("BOOK_NO_LICENSE");
    }

    @Test
    void 非本人loan与不存在同映射404() {
        assertThat(handler.handleDomain(new LoanNotFoundException("x")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void body校验失败返回400带字段明细() throws Exception {
        var ctor = Sample.class.getConstructor(String.class);
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.rejectValue("bookId", "NotNull", "不能为空");
        var ex = new MethodArgumentNotValidException(new MethodParameter(ctor, 0), binding);

        ProblemDetail pd = handler.handleBodyValidation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pd.getProperty("errorCode")).isEqualTo("VALIDATION_FAILED");
        assertThat(pd.getProperty("fields")).isEqualTo(Map.of("bookId", "不能为空"));
    }

    static class Sample {
        public Sample(String ignored) {}
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=GlobalExceptionHandlerTest
```

Expected: COMPILATION ERROR

- [ ] **Step 3: 实现**（errorCode → HTTP 的唯一真相集中在这张表；领域层不感知 HTTP）

```java
package com.elibrary.common.api;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<ErrorCode, HttpStatus> STATUS_BY_CODE = Map.of(
            ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
            ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
            ErrorCode.BOOK_NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCode.LOAN_NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCode.BOOK_NO_LICENSE, HttpStatus.CONFLICT,
            ErrorCode.LOAN_LIMIT_REACHED, HttpStatus.CONFLICT,
            ErrorCode.LOAN_DUPLICATE, HttpStatus.CONFLICT,
            ErrorCode.LOAN_ALREADY_RETURNED, HttpStatus.CONFLICT,
            ErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT);
    // 注意：Map.of 上限 10 对，第 10 个 code 加入时换 Map.ofEntries 或 EnumMap

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        HttpStatus status = STATUS_BY_CODE.get(ex.getCode());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("errorCode", ex.getCode().name());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldErrorWrapper::fieldOf, FieldErrorWrapper::messageOf, (a, b) -> a));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求参数校验失败");
        pd.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        pd.setProperty("errorCode", ErrorCode.VALIDATION_FAILED.name());
        pd.setProperty("fields", fields);
        return pd;
    }

    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ProblemDetail handleBadRequest(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求参数非法: " + ex.getMessage());
        pd.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        pd.setProperty("errorCode", ErrorCode.VALIDATION_FAILED.name());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknown(Exception ex) {
        log.error("未预期异常", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误");
        pd.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        return pd;
    }

    // FieldError 的 getter 命名冲突（getDefaultMessage 可能为 null），收拢在这里
    private static final class FieldErrorWrapper {
        static String fieldOf(org.springframework.validation.FieldError e) { return e.getField(); }
        static String messageOf(org.springframework.validation.FieldError e) {
            return e.getDefaultMessage() == null ? "非法" : e.getDefaultMessage();
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
mvn test -Dtest=GlobalExceptionHandlerTest
```

Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: RFC 7807 全局异常映射（errorCode→HTTP 集中一处）"
```

---

### Task 9: 种子用户 + login 端点

**Files:**
- Create: `src/main/java/com/elibrary/user/domain/InvalidCredentialsException.java`
- Create: `src/main/java/com/elibrary/user/application/UsersSeeder.java`
- Create: `src/main/java/com/elibrary/user/application/AuthService.java`
- Create: `src/main/java/com/elibrary/user/api/AuthController.java`
- Create: `src/main/java/com/elibrary/user/api/LoginRequest.java`
- Create: `src/main/java/com/elibrary/user/api/LoginResponse.java`
- Test: `src/test/java/com/elibrary/user/application/AuthServiceTest.java`
- Test: `src/test/java/com/elibrary/user/api/AuthControllerTest.java`
- Test: `src/test/java/com/elibrary/user/LoginIT.java`

- [ ] **Step 1: 写 AuthService 失败测试**

```java
package com.elibrary.user.application;

import com.elibrary.user.domain.InvalidCredentialsException;
import com.elibrary.user.domain.User;
import com.elibrary.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserMapper userMapper;
    @Spy BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    @Test
    void 正确凭证返回token() {
        User alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        alice.setPassword(encoder.encode("password123"));
        when(userMapper.selectOne(any())).thenReturn(alice);
        when(jwtService.generate(1L)).thenReturn("jwt-token");

        assertThat(authService.login("alice", "password123")).isEqualTo("jwt-token");
    }

    @Test
    void 密码错误抛InvalidCredentials() {
        User alice = new User();
        alice.setId(1L);
        alice.setPassword(encoder.encode("password123"));
        when(userMapper.selectOne(any())).thenReturn(alice);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 用户不存在与密码错误同响应防枚举() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> authService.login("nobody", "x"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=AuthServiceTest
```

Expected: COMPILATION ERROR

- [ ] **Step 3: 实现**

```java
package com.elibrary.user.domain;

import com.elibrary.common.domain.DomainException;
import com.elibrary.common.domain.ErrorCode;

public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException(String message) { super(ErrorCode.INVALID_CREDENTIALS, message); }
}
```

```java
package com.elibrary.user.application;

import com.elibrary.user.domain.User;
import com.elibrary.user.mapper.UserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// BCrypt 哈希无法静态生成，种子用户在启动时编码后插入（不走 Flyway）
@Component
public class UsersSeeder implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UsersSeeder(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        userMapper.insert(user("alice"));
        userMapper.insert(user("bob"));
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password123"));
        return user;
    }
}
```

```java
package com.elibrary.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.elibrary.user.domain.InvalidCredentialsException;
import com.elibrary.user.domain.User;
import com.elibrary.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            // 用户不存在与密码错误同错误：防用户名枚举
            throw new InvalidCredentialsException("用户名或密码错误");
        }
        return jwtService.generate(user.getId());
    }
}
```

```java
package com.elibrary.user.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(authService.login(request.username(), request.password()));
    }
}
```

```java
package com.elibrary.user.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
```

```java
package com.elibrary.user.api;

public record LoginResponse(String token) {}
```

- [ ] **Step 4: Web 切片测试**（`addFilters=false`：切片聚焦序列化与映射，安全链路已由 SecurityIT 覆盖）

```java
package com.elibrary.user.api;

import com.elibrary.user.application.AuthService;
import com.elibrary.user.domain.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @Test
    void 登录成功返回token() throws Exception {
        when(authService.login("alice", "password123")).thenReturn("jwt-token");
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void 登录失败返回401_problem_json带errorCode() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new InvalidCredentialsException("用户名或密码错误"));
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 缺字段返回400带字段明细() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.password").exists());
    }
}
```

- [ ] **Step 5: 端到端 IT**

```java
package com.elibrary.user;

import com.elibrary.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class LoginIT extends AbstractIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 种子用户登录成功() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void 错误密码401() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }
}
```

- [ ] **Step 6: 运行全部相关测试**

```bash
mvn test -Dtest='AuthServiceTest,AuthControllerTest'
mvn verify -Dtest=SkipThis -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LoginIT
```

Expected: 分别 `Tests run: 3` / `Tests run: 3`，全绿

- [ ] **Step 7: Commit**

```bash
git add src/
git commit -m "feat: 种子用户与 login 端点（JWT 签发）"
```

---

### Task 10: BookFacade + BookService（浏览/详情）

**Files:**
- Create: `src/main/java/com/elibrary/book/api/BookFacade.java`
- Create: `src/main/java/com/elibrary/book/application/BookFacadeImpl.java`
- Create: `src/main/java/com/elibrary/book/application/BookService.java`
- Create: `src/main/java/com/elibrary/book/api/BookSummaryResponse.java`
- Create: `src/main/java/com/elibrary/book/api/BookDetailResponse.java`
- Create: `src/main/java/com/elibrary/common/api/PageResponse.java`
- Test: `src/test/java/com/elibrary/book/application/BookFacadeImplTest.java`
- Test: `src/test/java/com/elibrary/book/application/BookServiceTest.java`

- [ ] **Step 1: 写 BookFacadeImpl 失败测试**（乐观锁重试语义：updateById 返回 0 行 → reload 重试一次 → 再失败抛冲突）

```java
package com.elibrary.book.application;

import com.elibrary.book.domain.Book;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.domain.NoLicenseException;
import com.elibrary.book.mapper.BookMapper;
import com.elibrary.common.domain.OptimisticLockConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookFacadeImplTest {

    @Mock BookMapper bookMapper;
    @InjectMocks BookFacadeImpl facade;

    @Test
    void 书不存在抛404() {
        when(bookMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> facade.getBook(9L)).isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 预留名额成功() {
        Book book = book(1L, 3, 3);
        when(bookMapper.updateById(any(Book.class))).thenReturn(1);
        facade.reserveLicense(book);
        assertThat(book.getAvailableLicenses()).isEqualTo(2);
        verify(bookMapper).updateById(book);
    }

    @Test
    void 乐观锁冲突自动重试一次_重试成功() {
        Book stale = book(1L, 3, 2);
        Book fresh = book(1L, 3, 3);
        when(bookMapper.updateById(any(Book.class))).thenReturn(0, 1); // 第一次冲突，第二次成功
        when(bookMapper.selectById(1L)).thenReturn(fresh);

        facade.reserveLicense(stale);

        verify(bookMapper, times(2)).updateById(any(Book.class));
    }

    @Test
    void 重试后仍冲突抛CONCURRENT_MODIFICATION() {
        Book book = book(1L, 3, 3);
        when(bookMapper.updateById(any(Book.class))).thenReturn(0, 0);
        when(bookMapper.selectById(1L)).thenReturn(book);

        assertThatThrownBy(() -> facade.reserveLicense(book))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    @Test
    void 名额耗尽抛NoLicense且不update() {
        Book book = book(1L, 1, 0);
        assertThatThrownBy(() -> facade.reserveLicense(book)).isInstanceOf(NoLicenseException.class);
        verify(bookMapper, never()).updateById(any());
    }

    @Test
    void 释放名额() {
        Book book = book(1L, 3, 2);
        when(bookMapper.updateById(any(Book.class))).thenReturn(1);
        when(bookMapper.selectById(1L)).thenReturn(book);
        facade.releaseLicense(1L);
        verify(bookMapper).updateById(any(Book.class));
    }

    private Book book(long id, int total, int available) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("重构");
        book.setTotalLicenses(total);
        book.setAvailableLicenses(available);
        book.setVersion(0);
        return book;
    }
}
```

- [ ] **Step 2: 写 BookService 失败测试**

```java
package com.elibrary.book.application;

import com.elibrary.book.api.BookDetailResponse;
import com.elibrary.book.domain.Book;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.mapper.BookMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock BookMapper bookMapper;
    @InjectMocks BookService bookService;

    @Test
    void 详情带borrowedByYou() {
        Book book = book(1L, 3, 1);
        when(bookMapper.selectById(1L)).thenReturn(book);
        when(bookMapper.existsActiveBorrow(1L, 42L)).thenReturn(true);

        BookDetailResponse detail = bookService.getDetail(1L, 42L);

        assertThat(detail.borrowedByYou()).isTrue();
        assertThat(detail.availableLicenses()).isEqualTo(1);
    }

    @Test
    void 详情不存在抛404() {
        when(bookMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> bookService.getDetail(9L, 42L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 检索透传分页参数并映射响应() {
        Page<Book> page = new Page<>(1, 10);
        page.setRecords(List.of(book(1L, 3, 2)));
        page.setTotal(1);
        when(bookMapper.selectPage(any(), any())).thenReturn(page);

        var response = bookService.search("重构", "TECHNOLOGY", 1, 10);

        assertThat(response.content()).hasSize(1);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.content().get(0).availableLicenses()).isEqualTo(2);
    }

    private Book book(long id, int total, int available) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("重构");
        book.setAuthor("Martin Fowler");
        book.setCategory("TECHNOLOGY");
        book.setTotalLicenses(total);
        book.setAvailableLicenses(available);
        return book;
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
mvn test -Dtest='BookFacadeImplTest,BookServiceTest'
```

Expected: COMPILATION ERROR

- [ ] **Step 4: 实现 DTO 与 Facade**

```java
package com.elibrary.book.api;

public record BookSummaryResponse(Long id, String title, String author, String category,
                                   int availableLicenses) {}
```

```java
package com.elibrary.book.api;

public record BookDetailResponse(Long id, String title, String author, String category,
                                  String description, int availableLicenses, boolean borrowedByYou) {}
```

```java
package com.elibrary.common.api;

import java.util.List;

public record PageResponse<T>(List<T> content, long total, int page, int size) {}
```

```java
package com.elibrary.book.api;

import com.elibrary.book.domain.Book;

// 写侧跨 feature 唯一通道：拆分时把实现换成 REST 客户端，LoanService 一行不改
public interface BookFacade {
    Book getBook(Long id);
    void reserveLicense(Book book);
    void releaseLicense(Long bookId);
}
```

```java
package com.elibrary.book.application;

import com.elibrary.book.api.BookFacade;
import com.elibrary.book.domain.Book;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.mapper.BookMapper;
import com.elibrary.common.domain.OptimisticLockConflictException;
import org.springframework.stereotype.Service;

@Service
public class BookFacadeImpl implements BookFacade {

    private static final int MAX_ATTEMPTS = 2; // 乐观锁假冲突（名额充足但 version 撞）重试一次

    private final BookMapper bookMapper;

    public BookFacadeImpl(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    @Override
    public Book getBook(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BookNotFoundException("书籍不存在: id=" + id);
        }
        return book;
    }

    @Override
    public void reserveLicense(Book book) {
        updateWithRetry(book, Book::borrow, "扣减借阅名额失败: book=" + book.getId());
    }

    @Override
    public void releaseLicense(Long bookId) {
        updateWithRetry(getBook(bookId), Book::release, "释放借阅名额失败: book=" + bookId);
    }

    private void updateWithRetry(Book book, java.util.function.Consumer<Book> action, String conflictMessage) {
        Book current = book;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            action.accept(current);
            if (bookMapper.updateById(current) == 1) {
                return;
            }
            current = getBook(book.getId()); // 冲突后 reload 新版本再试
        }
        throw new OptimisticLockConflictException(conflictMessage);
    }
}
```

- [ ] **Step 5: 实现 BookService**

```java
package com.elibrary.book.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elibrary.book.api.BookDetailResponse;
import com.elibrary.book.api.BookSummaryResponse;
import com.elibrary.book.domain.Book;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.book.mapper.BookMapper;
import com.elibrary.common.api.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class BookService {

    private final BookMapper bookMapper;

    public BookService(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    public PageResponse<BookSummaryResponse> search(String keyword, String category, int page, int size) {
        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // title/author 任一命中；utf8mb4 CI collation 天然大小写不敏感
            wrapper.and(q -> q.like(Book::getTitle, keyword).or().like(Book::getAuthor, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Book::getCategory, category);
        }
        Page<Book> result = bookMapper.selectPage(Page.of(page, size), wrapper);
        List<BookSummaryResponse> content = result.getRecords().stream()
                .map(b -> new BookSummaryResponse(b.getId(), b.getTitle(), b.getAuthor(),
                        b.getCategory(), b.getAvailableLicenses()))
                .toList();
        return new PageResponse<>(content, result.getTotal(), page, size);
    }

    public BookDetailResponse getDetail(Long id, Long userId) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BookNotFoundException("书籍不存在: id=" + id);
        }
        boolean borrowedByYou = bookMapper.existsActiveBorrow(id, userId);
        return new BookDetailResponse(book.getId(), book.getTitle(), book.getAuthor(),
                book.getCategory(), book.getDescription(), book.getAvailableLicenses(), borrowedByYou);
    }
}
```

- [ ] **Step 6: 运行确认通过**（OptimisticLockConflictException 挪包后 `mvn test` 全量跑一遍防遗漏引用）

```bash
mvn test
```

Expected: 全绿

- [ ] **Step 7: Commit**

```bash
git add src/
git commit -m "feat: BookFacade（乐观锁重试）与浏览/详情服务"
```

---

### Task 11: BookController（Web 切片）

**Files:**
- Create: `src/main/java/com/elibrary/book/api/BookController.java`
- Create: `src/test/java/com/elibrary/security/WithMockUserId.java`
- Test: `src/test/java/com/elibrary/book/api/BookControllerTest.java`

- [ ] **Step 1: 测试基建 WithMockUserId**（principal 是 Long userId，与 JwtAuthFilter 写入的形态一致）

```java
package com.elibrary.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockUserId.Factory.class)
public @interface WithMockUserId {

    long value() default 1L;

    class Factory implements WithSecurityContextFactory<WithMockUserId> {
        @Override
        public SecurityContext createSecurityContext(WithMockUserId annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(annotation.value(), null, List.of()));
            return context;
        }
    }
}
```

- [ ] **Step 2: 写 BookController 失败测试**

```java
package com.elibrary.book.api;

import com.elibrary.book.application.BookService;
import com.elibrary.book.domain.BookNotFoundException;
import com.elibrary.security.WithMockUserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookService bookService;

    @Test
    void 列表200() throws Exception {
        when(bookService.search(null, null, 1, 10)).thenReturn(
                new com.elibrary.common.api.PageResponse<>(java.util.List.of(), 0, 1, 10));
        mockMvc.perform(get("/api/books").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void 页码非法返回400_不悄悄钳位() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "0").param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void 详情404_problem_json() throws Exception {
        when(bookService.getDetail(9L, 1L)).thenThrow(new BookNotFoundException("书籍不存在: id=9"));
        mockMvc.perform(get("/api/books/9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("BOOK_NOT_FOUND"));
    }

    @Test
    @WithMockUserId(7)
    void 详情当前用户从principal解出() throws Exception {
        when(bookService.getDetail(1L, 7L)).thenReturn(new BookDetailResponse(
                1L, "重构", "Martin Fowler", "TECHNOLOGY", "desc", 2, false));
        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrowedByYou").value(false));
        verify(bookService).getDetail(1L, 7L);
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
mvn test -Dtest=BookControllerTest
```

Expected: COMPILATION ERROR

- [ ] **Step 4: 实现 BookController**

```java
package com.elibrary.book.api;

import com.elibrary.book.application.BookService;
import com.elibrary.common.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/api/books")
    public PageResponse<BookSummaryResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam @Min(1) int page,
            @RequestParam @Min(1) @Max(100) int size) {
        return bookService.search(keyword, category, page, size);
    }

    @GetMapping("/api/books/{id}")
    public BookDetailResponse detail(@PathVariable Long id,
                                     @AuthenticationPrincipal Long userId) {
        return bookService.getDetail(id, userId);
    }
}
```

- [ ] **Step 5: 运行确认通过**

```bash
mvn test -Dtest=BookControllerTest
```

Expected: `Tests run: 4, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: 书籍浏览/详情 API（分页校验 400 不钳位）"
```

---

### Task 12: UserFacade + LoanService.borrow（借阅校验链）

**Files:**
- Create: `src/main/java/com/elibrary/user/api/UserFacade.java`
- Create: `src/main/java/com/elibrary/user/application/UserFacadeImpl.java`
- Create: `src/main/java/com/elibrary/loan/application/LoanService.java`
- Test: `src/test/java/com/elibrary/loan/application/LoanServiceBorrowTest.java`

- [ ] **Step 1: 写失败测试**（重点：校验链顺序 InOrder + 四条 409/404 路径 + 唯一索引兜底翻译）

```java
package com.elibrary.loan.application;

import com.elibrary.book.domain.Book;
import com.elibrary.loan.domain.Loan;
import com.elibrary.loan.domain.LoanDuplicateException;
import com.elibrary.loan.domain.LoanLimitException;
import com.elibrary.loan.mapper.LoanMapper;
import com.elibrary.user.api.UserFacade;
import com.elibrary.book.api.BookFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceBorrowTest {

    @Mock UserFacade userFacade;
    @Mock BookFacade bookFacade;
    @Mock LoanMapper loanMapper;
    @InjectMocks LoanService loanService;

    @Test
    void 借阅成功_校验链顺序固定() {
        when(bookFacade.getBook(2L)).thenReturn(book(2L));
        when(loanMapper.countActiveByUser(1L)).thenReturn(0L);
        when(loanMapper.existsActiveByUserAndBook(1L, 2L)).thenReturn(false);

        Loan loan = loanService.borrow(1L, 2L);

        assertThat(loan.getUserId()).isEqualTo(1L);
        assertThat(loan.getDueAt()).isEqualTo(loan.getBorrowedAt().plusDays(14));
        // 顺序即设计：先 user 锁，再加载书，再检查，最后扣名额插记录
        InOrder order = inOrder(userFacade, bookFacade, loanMapper);
        order.verify(userFacade).lockById(1L);
        order.verify(bookFacade).getBook(2L);
        order.verify(loanMapper).countActiveByUser(1L);
        order.verify(loanMapper).existsActiveByUserAndBook(1L, 2L);
        order.verify(bookFacade).reserveLicense(any(Book.class));
        order.verify(loanMapper).insert(any(Loan.class));
    }

    @Test
    void 在借满5本抛LOAN_LIMIT_REACHED() {
        when(bookFacade.getBook(2L)).thenReturn(book(2L));
        when(loanMapper.countActiveByUser(1L)).thenReturn(5L);

        assertThatThrownBy(() -> loanService.borrow(1L, 2L)).isInstanceOf(LoanLimitException.class);
        verify(bookFacade, never()).reserveLicense(any());
    }

    @Test
    void 重复借同书抛LOAN_DUPLICATE() {
        when(bookFacade.getBook(2L)).thenReturn(book(2L));
        when(loanMapper.countActiveByUser(1L)).thenReturn(0L);
        when(loanMapper.existsActiveByUserAndBook(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> loanService.borrow(1L, 2L)).isInstanceOf(LoanDuplicateException.class);
        verify(bookFacade, never()).reserveLicense(any());
    }

    @Test
    void 唯一索引兜底命中翻译为LOAN_DUPLICATE() {
        when(bookFacade.getBook(2L)).thenReturn(book(2L));
        when(loanMapper.countActiveByUser(1L)).thenReturn(0L);
        when(loanMapper.existsActiveByUserAndBook(1L, 2L)).thenReturn(false);
        doThrow(new DuplicateKeyException("uq_loans_user_active_book"))
                .when(loanMapper).insert(any(Loan.class));

        assertThatThrownBy(() -> loanService.borrow(1L, 2L)).isInstanceOf(LoanDuplicateException.class);
    }

    private Book book(long id) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("重构");
        book.setTotalLicenses(3);
        book.setAvailableLicenses(3);
        book.setVersion(0);
        return book;
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=LoanServiceBorrowTest
```

Expected: COMPILATION ERROR

- [ ] **Step 3: 实现 UserFacade / LoanService**

```java
package com.elibrary.user.api;

public interface UserFacade {
    /** 悲观锁锚点：借阅链路第一步，串行化同用户操作 */
    void lockById(Long userId);
}
```

```java
package com.elibrary.user.application;

import com.elibrary.user.api.UserFacade;
import com.elibrary.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserFacadeImpl implements UserFacade {

    private final UserMapper userMapper;

    public UserFacadeImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void lockById(Long userId) {
        // 行不存在属于 token 指向已删用户的防御分支，不是业务规则
        if (userMapper.selectByIdForUpdate(userId) == null) {
            throw new IllegalStateException("用户不存在: " + userId);
        }
    }
}
```

```java
package com.elibrary.loan.application;

import com.elibrary.book.api.BookFacade;
import com.elibrary.book.domain.Book;
import com.elibrary.loan.domain.Loan;
import com.elibrary.loan.domain.LoanDuplicateException;
import com.elibrary.loan.domain.LoanLimitException;
import com.elibrary.loan.domain.LoanPolicy;
import com.elibrary.loan.mapper.LoanMapper;
import com.elibrary.user.api.UserFacade;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class LoanService {

    private final UserFacade userFacade;
    private final BookFacade bookFacade;
    private final LoanMapper loanMapper;

    public LoanService(UserFacade userFacade, BookFacade bookFacade, LoanMapper loanMapper) {
        this.userFacade = userFacade;
        this.bookFacade = bookFacade;
        this.loanMapper = loanMapper;
    }

    /**
     * 校验链顺序即并发设计（design_walkthrough §3）：
     * user 行锁 → 加载书 → 在借数检查 → 重复借检查 → 扣名额 → 插入。
     */
    @Transactional
    public Loan borrow(Long userId, Long bookId) {
        userFacade.lockById(userId);
        Book book = bookFacade.getBook(bookId);
        if (loanMapper.countActiveByUser(userId) >= LoanPolicy.MAX_ACTIVE_LOANS) {
            throw new LoanLimitException("在借已达上限: " + LoanPolicy.MAX_ACTIVE_LOANS);
        }
        if (loanMapper.existsActiveByUserAndBook(userId, bookId)) {
            throw new LoanDuplicateException("已借阅此书，不可重复借阅");
        }
        bookFacade.reserveLicense(book);
        Loan loan = Loan.borrow(userId, bookId, LocalDateTime.now(ZoneOffset.UTC));
        try {
            loanMapper.insert(loan);
        } catch (DuplicateKeyException e) {
            // 并发双击穿透应用检查时，生成列唯一索引兜底；仅翻译目标约束，其他冲突不吞并保留根因
            if (e.getMessage() != null && e.getMessage().contains("uq_loans_user_active_book")) {
                throw new LoanDuplicateException("重复借阅（唯一约束兜底命中）", e);
            }
            throw e;
        }
        return loan;
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
mvn test -Dtest=LoanServiceBorrowTest
```

Expected: `Tests run: 4, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: 借阅校验链（user 锁→书→上限→重复→扣名额→插入）"
```

---

### Task 13: 归还 + 在借列表 + LoanController

**Files:**
- Modify: `src/main/java/com/elibrary/loan/application/LoanService.java`（追加两个方法）
- Create: `src/main/java/com/elibrary/loan/api/LoanController.java`
- Create: `src/main/java/com/elibrary/loan/api/BorrowRequest.java`
- Create: `src/main/java/com/elibrary/loan/api/LoanResponse.java`
- Test: `src/test/java/com/elibrary/loan/application/LoanServiceReturnTest.java`
- Test: `src/test/java/com/elibrary/loan/api/LoanControllerTest.java`

- [ ] **Step 1: 写归还/列表失败测试**

```java
package com.elibrary.loan.application;

import com.elibrary.book.api.BookFacade;
import com.elibrary.book.domain.Book;
import com.elibrary.loan.domain.Loan;
import com.elibrary.loan.domain.LoanAlreadyReturnedException;
import com.elibrary.loan.domain.LoanNotFoundException;
import com.elibrary.loan.mapper.LoanMapper;
import com.elibrary.loan.mapper.LoanWithBookView;
import com.elibrary.user.api.UserFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceReturnTest {

    @Mock UserFacade userFacade;
    @Mock BookFacade bookFacade;
    @Mock LoanMapper loanMapper;
    @InjectMocks LoanService loanService;

    @Test
    void 非本人loan与不存在同返回404_防越权探测() {
        when(loanMapper.selectById(1L)).thenReturn(activeLoan(1L, 1L, 2L)); // bob(2) 的借阅
        when(loanMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> loanService.returnLoan(1L, 1L)).isInstanceOf(LoanNotFoundException.class);
        assertThatThrownBy(() -> loanService.returnLoan(1L, 9L)).isInstanceOf(LoanNotFoundException.class);
        verify(bookFacade, never()).releaseLicense(any());
    }

    @Test
    void 已归还再归还抛409() {
        Loan returned = activeLoan(1L, 1L, 2L);
        returned.markReturned(LocalDateTime.now(ZoneOffset.UTC));
        when(loanMapper.selectById(1L)).thenReturn(returned);

        assertThatThrownBy(() -> loanService.returnLoan(1L, 1L))
                .isInstanceOf(LoanAlreadyReturnedException.class);
        verify(bookFacade, never()).releaseLicense(any());
    }

    @Test
    void 归还成功_同事务更新loan并释放名额() {
        Loan loan = activeLoan(1L, 1L, 2L);
        when(loanMapper.selectById(1L)).thenReturn(loan);
        Book book = new Book();
        book.setId(2L);
        book.setTitle("重构");
        book.setAuthor("Martin Fowler");
        when(bookFacade.getBook(2L)).thenReturn(book);

        var response = loanService.returnLoan(1L, 1L);

        assertThat(response.returnedAt()).isNotNull();
        verify(loanMapper).updateById(loan);
        verify(bookFacade).releaseLicense(2L);
    }

    @Test
    void 在借列表带派生overdue() {
        LoanWithBookView overdue = new LoanWithBookView();
        overdue.setId(1L);
        overdue.setBookId(2L);
        overdue.setTitle("重构");
        overdue.setAuthor("Martin Fowler");
        overdue.setBorrowedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(20));
        overdue.setDueAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(6)); // 已过 dueAt
        when(loanMapper.selectActiveWithBook(1L)).thenReturn(List.of(overdue));

        var loans = loanService.currentLoans(1L);

        assertThat(loans).hasSize(1);
        assertThat(loans.get(0).overdue()).isTrue();
    }

    private Loan activeLoan(long id, long userId, long bookId) {
        Loan loan = Loan.borrow(userId, bookId, LocalDateTime.now(ZoneOffset.UTC));
        loan.setId(id);
        return loan;
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=LoanServiceReturnTest
```

Expected: COMPILATION ERROR

- [ ] **Step 3: LoanService 追加方法**

```java
    /**
     * 归还：归属校验（非本人 404 防探测）→ 状态机校验（重复归还 409）→ 更新 loan → 释放名额。
     * 不取 user 锁只碰 Book：依赖方向 user→book 单向无环，无死锁。
     */
    @Transactional
    public LoanResponse returnLoan(Long userId, Long loanId) {
        Loan loan = loanMapper.selectById(loanId);
        if (loan == null || !loan.getUserId().equals(userId)) {
            throw new LoanNotFoundException("借阅记录不存在: id=" + loanId);
        }
        loan.markReturned(LocalDateTime.now(ZoneOffset.UTC));
        loanMapper.updateById(loan);
        bookFacade.releaseLicense(loan.getBookId());
        Book book = bookFacade.getBook(loan.getBookId());
        return new LoanResponse(loan.getId(), loan.getBookId(), book.getTitle(), book.getAuthor(),
                loan.getBorrowedAt(), loan.getDueAt(), loan.getReturnedAt(), false);
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> currentLoans(Long userId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return loanMapper.selectActiveWithBook(userId).stream()
                .map(v -> new LoanResponse(v.getId(), v.getBookId(), v.getTitle(), v.getAuthor(),
                        v.getBorrowedAt(), v.getDueAt(), v.getReturnedAt(), v.getDueAt().isBefore(now)))
                .toList();
    }
```

`LoanService` 文件头补 import：`java.util.List`、`com.elibrary.loan.api.LoanResponse`。

```java
package com.elibrary.loan.api;

import java.time.LocalDateTime;

public record LoanResponse(Long id, Long bookId, String title, String author,
                           LocalDateTime borrowedAt, LocalDateTime dueAt,
                           LocalDateTime returnedAt, boolean overdue) {}
```

```java
package com.elibrary.loan.api;

import jakarta.validation.constraints.NotNull;

public record BorrowRequest(@NotNull Long bookId) {}
```

- [ ] **Step 4: LoanController + 切片测试**

```java
package com.elibrary.loan.api;

import com.elibrary.loan.application.LoanService;
import com.elibrary.loan.domain.Loan;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    public ResponseEntity<LoanResponse> borrow(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody BorrowRequest request) {
        Loan loan = loanService.borrow(userId, request.bookId());
        return ResponseEntity
                .created(URI.create("/api/loans/" + loan.getId()))
                .body(toResponse(loan));
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnLoan(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return loanService.returnLoan(userId, id);
    }

    @GetMapping
    public List<LoanResponse> currentLoans(@AuthenticationPrincipal Long userId) {
        return loanService.currentLoans(userId);
    }

    // 借阅刚创建时书名信息不在 Loan 上，借阅响应是领域视图（无书名冗余），与列表/归还响应的字段差异在 README 说明
    private LoanResponse toResponse(Loan loan) {
        return new LoanResponse(loan.getId(), loan.getBookId(), null, null,
                loan.getBorrowedAt(), loan.getDueAt(), loan.getReturnedAt(), false);
    }
}
```

```java
package com.elibrary.loan.api;

import com.elibrary.loan.application.LoanService;
import com.elibrary.loan.domain.Loan;
import com.elibrary.security.WithMockUserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoanController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoanControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LoanService loanService;

    @Test
    @WithMockUserId(1)
    void 借阅返回201带Location() throws Exception {
        Loan loan = Loan.borrow(1L, 2L, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        loan.setId(10L);
        when(loanService.borrow(1L, 2L)).thenReturn(loan);

        mockMvc.perform(post("/api/loans").contentType(APPLICATION_JSON)
                        .content("{\"bookId\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/loans/10"))
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUserId(1)
    void bookId为空返回400带字段明细() throws Exception {
        mockMvc.perform(post("/api/loans").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.bookId").exists());
    }

    @Test
    @WithMockUserId(2)
    void 非本人loan返回404() throws Exception {
        when(loanService.returnLoan(2L, 5L))
                .thenThrow(new com.elibrary.loan.domain.LoanNotFoundException("借阅记录不存在: id=5"));
        mockMvc.perform(post("/api/loans/5/return"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_NOT_FOUND"));
    }

    @Test
    @WithMockUserId(1)
    void 在借列表200() throws Exception {
        when(loanService.currentLoans(1L)).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
```

- [ ] **Step 5: 运行全部新测试**

```bash
mvn test -Dtest='LoanServiceReturnTest,LoanControllerTest'
```

Expected: `Tests run: 4` / `Tests run: 4`，全绿

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: 归还与在借列表 API（非本人 404、overdue 派生）"
```

---

### Task 14: 并发正确性集成测试（四竞态，真 MySQL）

**Files:**
- Test: `src/test/java/com/elibrary/loan/LoanConcurrencyIT.java`

- [ ] **Step 1: 写四个竞态测试**（每个 @Test 前 重置 loans 与 books 名额，测试互不依赖）

```java
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanConcurrencyIT extends AbstractIT {

    @Autowired LoanService loanService;
    @Autowired BookMapper bookMapper;
    @Autowired UserMapper userMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 重置数据() {
        counter.set(0); // 竞态测试用共享计数器取下标，跨测试必须复位
        jdbcTemplate.execute("DELETE FROM loans");
        jdbcTemplate.execute("UPDATE books SET available_licenses = total_licenses, version = 0");
    }

    @Test
    void 兜底约束_绕过服务直插重复在借_生成列唯一索引拦截() {
        Long aliceId = userIdByUsername("alice");
        Long bookId = bookIdByTitle("三体");
        String insert = "INSERT INTO loans (user_id, book_id, borrowed_at, due_at) "
                + "VALUES (?, ?, NOW(), DATE_ADD(NOW(), INTERVAL 14 DAY))";
        jdbcTemplate.update(insert, aliceId, bookId);
        // user 行锁只串行化服务路径；约束本身要能独立拦住绕过服务的重复在借
        assertThatThrownBy(() -> jdbcTemplate.update(insert, aliceId, bookId))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void 竞态A_N人抢最后一个名额_恰好一人成功绝不超卖() throws Exception {
        Long bookId = bookIdByTitle("代码整洁之道"); // 总共 1 个名额
        List<Long> users = createUsers(8);

        List<String> results = runConcurrent(8,
                () -> tryBorrow(users.get(counter.getAndIncrement()), bookId));

        assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
        assertThat(results.stream().filter("BOOK_NO_LICENSE"::equals).count()).isEqualTo(7);
        assertThat(bookMapper.selectById(bookId).getAvailableLicenses()).isZero();
    }

    @Test
    void 竞态C_同用户并发双击_恰好一笔在借() throws Exception {
        Long aliceId = userIdByUsername("alice");
        Long bookId = bookIdByTitle("领域驱动设计");

        List<String> results = runConcurrent(2, () -> tryBorrow(aliceId, bookId));

        assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
        assertThat(results).containsExactlyInAnyOrder("OK", "LOAN_DUPLICATE");
        assertThat(loanCount(aliceId, bookId)).isEqualTo(1);
    }

    @Test
    void 竞态D_并发借到第6本_上限拦截() throws Exception {
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
    void 竞态E_并发重复归还_一次成功一次409_名额不双放() throws Exception {
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

        assertThat(results).containsExactlyInAnyOrder("OK", "LOAN_ALREADY_RETURNED");
        assertThat(bookMapper.selectById(bookId).getAvailableLicenses()).isEqualTo(5);
    }

    // ---- helpers ----

    private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();

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
            List<Future<String>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown(); // 所有线程同点起跑，最大化竞态窗口
            List<String> out = new java.util.ArrayList<>();
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
```

- [ ] **Step 2: 运行**（需 Docker；这是全项目最有说服力的一组测试）

```bash
mvn verify -Dtest=SkipThis -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LoanConcurrencyIT
```

Expected: `Tests run: 5, Failures: 0`（任何一枚失败都说明锁策略或约束有洞，先修再继续）

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "test: 四竞态并发集成测试（真 MySQL 验证锁策略）"
```

---

### Task 15: README 与最终验证

**Files:**
- Create: `README.md`（工程根目录，简体中文，结构按 design_walkthrough §13）

- [ ] **Step 1: 写 README**，各节必含内容：

1. **项目概述**：一段话 + 5 功能列表
2. **快速开始**：环境要求（JDK 21、Maven、Docker）；数据库要求 **MySQL ≥ 8.0.16**（CHECK 约束生效的最低版本），示例与测试镜像统一钉在 8.4 LTS 以保证可复现；启动 MySQL（`docker run --name elibrary-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=elibrary -p 3306:3306 -d mysql:8.4`）；`mvn verify`（含集成测试）；`mvn spring-boot:run`；种子账号 alice/bob（password123）；三条 curl 示例（login→token、GET /api/books、POST /api/loans）含期望响应片段
3. **API 文档**：6 端点（方法/路径/入参/响应示例）+ 错误码全表（照 design_walkthrough §8 抄录，HTTP/errorCode/场景三列）
4. **Assumptions**：A1–A11 全文（照 requirements_analysis §3 抄录，逐条）
5. **设计决策**：
   - 许可模型（数字内容 = 并发许可，非实体副本）
   - 并发与锁（校验链、三竞态表、锁组合逻辑——"悲观保正确、乐观保吞吐、约束兜底防 bug"）
   - MySQL 适配（版本政策：兼容 8.0.16+，镜像钉 8.4 LTS；生成列等效 partial unique index 的 SQL 与原理，**附 PostgreSQL 原生写法对照**——展示跨库认知而非绑定单一数据库；CHECK 防超卖；CI collation 免 lower()）
   - 状态不落库（派生公式 + 为什么单一真相）
   - 错误处理（RFC 7807 + errorCode；非本人 404 的防探测理由）
   - **MyBatis-Plus vs Spring Data JPA 选型论证**（锁敏感场景 SQL 透明度优先；何时会反向选择 JPA）
6. **演进路径**（每项："何时需要 + 方案要点"）：
   - 幂等（Idempotency-Key + 请求指纹，资金划转类必须）
   - Redis/Kafka（规模化后热点书锁竞争、逾期通知事件的引入时机）
   - 限额/借期类别化（`@ConfigurationProperties` + by-category map 方案，含校验链重排提示——照 design_walkthrough §3 末节）
   - 微服务拆分（就绪资产清单、不变量数据归属分析、Saga 三步、/loans JOIN 两方案——照 §12 浓缩）

**红线自查**：README 不提任何项目外背景与开发环境信息，只谈工程本身。

- [ ] **Step 2: 全量验证**

```bash
mvn verify
```

Expected: 全部单测 + 全部 IT 绿（需 Docker）

- [ ] **Step 3: 手动冒烟**（真库启动，验证 curl 链路）

```bash
docker run --name elibrary-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=elibrary -p 3306:3306 -d mysql:8.4
mvn spring-boot:run
# 另开终端：
curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"alice","password":"password123"}'
# 期望 {"token":"eyJ..."}
curl -s localhost:8080/api/books?page=1&size=3
# 期望 6 本书的分页 JSON
curl -s -X POST localhost:8080/api/loans -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"bookId":1}'
# 期望 201；重复执行同命令 → 期望 409 LOAN_DUPLICATE
curl -s -X POST localhost:8080/api/loans/1/return -H "Authorization: Bearer <token>"
# 期望 200；再执行 → 期望 409 LOAN_ALREADY_RETURNED
```

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: README（假设、设计决策与演进路径）"
```

---

## 计划自审记录

1. **Spec 覆盖**：需求分析 §3 假设 A1（Task 3/10）、A2（Task 3 LoanPolicy）、A3/A4（Task 3+12+14）、A5/A6（A6 落在 V2 种子 category；A5 出范围仅 README Assumptions）、A7（Task 10/11）、A8（Task 6/7/9）、A9（全栈）、A10/A11（README §4/§6）。design_walkthrough §1–§13 全部有对应任务；§12/§13 落在 README 任务。
2. **类型一致性**：`BookFacade.getBook/reserveLicense/releaseLicense`、`UserFacade.lockById`、`LoanMapper.countActiveByUser/existsActiveByUserAndBook/selectActiveWithBook`、`JwtService.generate/parseUserId` 各任务间签名一致；`OptimisticLockConflictException` 自 Task 2 起定于 `common/domain`（跨聚合通用事实，避免中途挪包）。
3. **已知留白（有意为之，非遗漏）**：`BookMapperIT` 中断言 CI collation 的用例写作两段（like 不命中/小写命中），执行者若觉得别扭可简化为只留小写命中断言；Task 9 切片测试 `@MockitoBean` 需 Spring Boot 3.4+，与 pom 的 3.5.5 一致。

