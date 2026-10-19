# E-Library Service — 设计推演（v2）

> 开发前的功能逐个推演记录：流程、规则、并发竞态、边界情况、取舍理由。
> 前置输入：`requirements_analysis.md`（v2 定稿）。技术基线：Spring Boot + MyBatis-Plus + MySQL 8 + JWT。
> 过程文档，不随项目交付；最终对外交付物是工程根目录 README.md。

## 1. 浏览书籍 `GET /api/books?keyword=&category=&page=&size=`

- 列表必须带 `availableLicenses`（浏览的目的是决定是否借阅）
- 分页选 offset 而非 cursor：目录量级（几千~几万）深翻页代价可忽略，支持跳页对前端友好；cursor 是百万级数据的方案
- keyword 匹配 title/author：MySQL `utf8mb4_0900_ai_ci` 默认**大小写不敏感**，直接 `LIKE '%kw%'`，不需要 `lower()` 函数索引（PG 版设计才需要）；前缀通配本就用不上 B-tree 索引，此量级全扫可接受，fulltext index 作为规模化路径写进 README
- 分页与动态条件：MP `PaginationInnerInterceptor` + `LambdaQueryWrapper`（keyword/category 可选）
- 页码参数非法 → 400 VALIDATION_FAILED，不悄悄钳位（参数校验在系统边界做）

## 2. 查询详情 `GET /api/books/{id}`

- 详情须比列表多信息 → Book 加 description 字段，否则接口无意义
- 详情带 `borrowedByYou`（当前用户是否在借此书）：BookMapper 一条 JOIN loans 的投影查询，体现用户视角的 API 设计

## 3. 借阅 `POST /api/loans` body `{bookId}`

"创建借阅记录"是资源创建，比 `POST /books/{id}/borrow` 更 REST。

### 校验链（顺序固定，同一事务）

```
① 认证（JWT → 当前用户）
② 入参校验（bookId 非空）
③ SELECT user FOR UPDATE          ← 锁顺序第一步
④ 该用户在借数 < 5                → 409 LOAN_LIMIT_REACHED
⑤ 该用户未借过此书                → 409 LOAN_DUPLICATE
⑥ BookFacade.reserveLicense()     → 409 BOOK_NO_LICENSE（乐观锁保护）
⑦ 创建 Loan(dueAt = now+14d)
⑧ 提交
```

### 三个竞态（MySQL 版）

| 竞态 | 场景 | 解法 |
|---|---|---|
| A 抢最后名额 | 两事务同读 available=1 | Book `@Version` 乐观锁一方失败 + `CHECK (available_licenses >= 0)` 兜底，绝不超卖 |
| B 乐观锁假冲突 | 名额充足但 version 冲突 | 自动重试一次（MP `updateById` 影响行数 = 0 视为冲突）；备选原子 UPDATE 因绕过聚合被否 |
| C 同用户并发双击 | 两请求同时过 ④⑤ 检查 | user 行悲观锁（`SELECT ... FOR UPDATE`）串行化同用户借阅；⑤（同书重复借）另有生成列唯一索引兜底，④（在借数上限）无 DB 约束可表达故必须靠锁 |

**锁组合的设计逻辑**：user 行悲观（保正确性，锁冷行无争用）+ book 行乐观（保吞吐，读多写少）+ DB 约束兜底（防应用 bug）。

### MySQL 适配：partial unique index 的等效实现

PG 的 `UNIQUE ... WHERE returned_at IS NULL` 在 MySQL 不存在，用生成列等效：

```sql
active_book_id BIGINT GENERATED ALWAYS AS (IF(returned_at IS NULL, book_id, NULL)) STORED,
UNIQUE KEY uq_loans_user_active_book (user_id, active_book_id)
```

MySQL 唯一索引忽略 NULL：已归还行 `active_book_id` 为 NULL 不受限；在借行按 (user, book) 唯一。语义与 partial unique index 完全等价，是 MySQL 8 惯用手法。

### MyBatis-Plus 落地要点

- 乐观锁：实体 `@Version` 字段 + `OptimisticLockerInnerInterceptor`；`updateById` 返回 0 行即冲突
- 悲观锁：`loan` 经 `user/api/UserFacade.lockById()` 获取，实现内调 `UserMapper.selectByIdForUpdate`（SQL 显式写 `FOR UPDATE`，是展示点而非负担）
- 重试边界：收在 `BookFacade` 实现内（reload + 重试一次），`LoanService` 不感知
- InnoDB RR 下，拿到 user 行锁后做普通 `COUNT` 是安全的：竞态事务必须先持有同一把 user 锁才能提交新 Loan，锁到手时刻之前的提交对 Count 可见，不需要 `COUNT ... FOR UPDATE`

### 幂等性

网络重试会被 409 LOAN_DUPLICATE 挡住（体验可接受）；严格幂等需 Idempotency-Key + 请求指纹存储。本项目不做，README 单列方案（资金划转类业务必须做）。

### 限额与借期配置化（考虑过，未做）

固定 5 本 / 14 天保持 LoanPolicy 常量；类别化限额（如 JOURNAL: 2）与类别化借期（如 JOURNAL: 7 天）只写进 README 演进节。未做的理由：需求只给出单一数字，没有第二类别规则的真实信号；而配置化要付三处连锁改动——校验链重排（书须先加载，category 才能参与 ④ 的检查，BOOK_NOT_FOUND 反而提前）、count 变按类别 JOIN、Saga 化时 reserve 响应需带回 category——为想象中的灵活性预付违反 YAGNI。
实现形态已想清供 README 引用：`@ConfigurationProperties`（default + by-category map + 启动校验，未配置类别回退 default）；规则表进 DB 是再下一步（运行时可调但引入管理面）。总量语义需注意：纯类别限额无全局帽子，上限 = Σ各类别限额。

## 4. 归还 `POST /api/loans/{id}/return`

- 不用 PATCH /loans/{id}：PATCH 语义是客户端设值，暴露状态机；POST /return 是服务端命令，转换规则留在领域内
- 流程：加载 Loan →（不存在 404）→ 归属校验（非本人 **404** 而非 403，防 loan id 越权探测）→（已归还 409）→ markReturned + `BookFacade.releaseLicense()` 同一事务
- 重复归还 409 vs 幂等 200：双归还是客户端 bug 信号选 409；README 讨论两种立场
- 归还不取 user 锁只碰 Book：依赖方向 user→book 单向无环，无死锁

## 5. 查看在借 `GET /api/loans?active=true`

- 无 userId 参数：当前用户从 JWT 解出 → IDOR 无从发生（优于校验式防越权）
- 一条 JOIN 查询直接映射 LoanView 投影，单 SQL 解决 N+1（MP 无 EntityGraph，JOIN 反而更直白）
- 响应带 `overdue` 派生字段
- MyBatis 对 record 的构造器映射支持不完善 → JOIN 投影允许 mapper 层视图 POJO；web 层 DTO 仍用 record

## 6. 认证 `POST /api/auth/login`

- 需求文档零字提认证，但 F5"查看目前在借"隐含 per-user 语境（推理见需求分析 I1）；无状态 JWT 同时利好未来微服务拆分 → 引入
- jjwt HS256，secret 走配置；`sub=userId, exp=24h`；Bearer token 过滤器解析后写 SecurityContext
- 用户来源：Flyway `V2__seed.sql` 种子用户（BCrypt 哈希），**不做注册/改密**——账户体系不属于图书馆域
- 登录失败 401 INVALID_CREDENTIALS；token 无效/过期 401（Security 层，无业务 errorCode）

## 7. 横切：状态不落库

```
active   := returnedAt == null
returned := returnedAt != null
overdue  := active && now > dueAt
```

不存 status enum：enum 与时间戳两份真相必然漂移。代价（查询写谓词）由 `(user_id, returned_at)` 复合索引解决。
整个系统唯一持久化的状态变化就是 `returnedAt` 一个时间戳。

## 8. 错误码全景

| HTTP | errorCode | 场景 |
|---|---|---|
| 401 | INVALID_CREDENTIALS | login 用户名/密码错误 |
| 401 | — | 无效/过期 token（Security 层） |
| 400 | VALIDATION_FAILED | 参数校验，带字段明细 |
| 404 | BOOK_NOT_FOUND / LOAN_NOT_FOUND | 含非本人 loan |
| 409 | BOOK_NO_LICENSE | 名额耗尽 |
| 409 | LOAN_LIMIT_REACHED | 超 5 本上限 |
| 409 | LOAN_DUPLICATE | 重复借（含唯一索引兜底命中） |
| 409 | LOAN_ALREADY_RETURNED | 重复归还 |
| 409 | CONCURRENT_MODIFICATION | 乐观锁冲突重试后仍失败 |

RFC 7807 `application/problem+json`，errorCode 为扩展字段；映射集中在 `common` 一处。

## 9. 代码组织与 facade 纪律

```
book/   api(BookController, BookFacade, DTO)  application(BookService, BookFacadeImpl)  domain(Book)  mapper(BookMapper)
loan/   api(LoanController, DTO)              application(LoanService)                 domain(Loan, LoanPolicy)  mapper(LoanMapper)
user/   api(AuthController)                   application(UserService, JwtService)     domain(User)  mapper(UserMapper)
common/ problem+json 封装、DomainException、全局异常映射、Security 配置
```

- **跨 feature 访问纪律（红线，分写侧/读侧）**：
  - **写侧**：`loan` 不得注入 `BookMapper`/`UserMapper` 或跨包改数据；只经 `book/api/BookFacade`（`reserveLicense / releaseLicense / getBook`）与 `user/api/UserFacade`（`lockById`），实现在各自 application 层、同事务参与（不得 REQUIRES_NEW）。这是 modular monolith 的抽取路径：拆分时把 facade 实现换成 REST 客户端，`LoanService` 一行不改
  - **读侧**：mapper 层允许跨表 JOIN 投影（LoanView JOIN books、borrowedByYou JOIN loans），投影对象留在发起 feature 的 mapper 层——这是**已声明的拆分债**（§12：拆分时改 API 组合或快照），换取单体下单 SQL 的简单
- 业务规则方法（`borrow()/release()/markReturned()`）留在实体上；MP Wrapper 只出现在查询组装处，不出现在业务判断处——防滑向贫血模型
- 规则数值（14 天、5 本）集中在 `loan` 包 policy，不散落

## 10. 数据库 schema（Flyway V1）

```sql
CREATE TABLE users (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64)  NOT NULL,
  password VARCHAR(100) NOT NULL,                 -- BCrypt
  UNIQUE KEY uq_users_username (username)
);

CREATE TABLE books (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  title             VARCHAR(255) NOT NULL,
  author            VARCHAR(128) NOT NULL,
  category          VARCHAR(64)  NOT NULL,
  description       TEXT,
  total_licenses    INT NOT NULL,
  available_licenses INT NOT NULL,
  version           INT NOT NULL DEFAULT 0,
  CHECK (available_licenses >= 0 AND available_licenses <= total_licenses),  -- 需 MySQL ≥ 8.0.16
  KEY idx_books_category (category)
);

CREATE TABLE loans (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  book_id      BIGINT NOT NULL,
  borrowed_at  DATETIME(6) NOT NULL,
  due_at       DATETIME(6) NOT NULL,
  returned_at  DATETIME(6) NULL,
  active_book_id BIGINT GENERATED ALWAYS AS (IF(returned_at IS NULL, book_id, NULL)) STORED,
  UNIQUE KEY uq_loans_user_active_book (user_id, active_book_id),
  KEY idx_loans_user_active (user_id, returned_at)   -- 在借数 COUNT 与在借列表
);
```

- 不加外键：为微服务拆分留路（跨库后 FK 必删），归属校验在应用层——README 说明
- `DATETIME(6)` 微秒精度；应用层统一 UTC 约定
- MP 实体映射生成列：`@TableField(insertStrategy/updateStrategy = NEVER)`

## 11. 测试策略（四层）

| 层 | 工具 | 覆盖 |
|---|---|---|
| 领域 | 纯 JUnit | 名额边界、重复归还、dueAt/overdue 派生、上限规则 |
| Service | Mockito | 校验链顺序、facade 协作、异常翻译、重试触发 |
| Web | @WebMvcTest | 7807 序列化、状态码映射、401、参数校验 400、当前用户解析 |
| 集成 | Testcontainers + 真 MySQL | 四竞态：N 并发抢最后名额恰好 1 成功；同用户并发双击恰好 1 成功；并发归还 200+409；第 6 笔 409 |

每个领域异常路径至少一个测试。

## 12. 微服务演进就绪度

**已就绪（不用改）**：package-by-feature 物理边界 ≈ 服务边界候选；JWT 无状态本地验签；领域规则无框架耦合；DTO record + 7807 契约显式；写侧经 facade 的访问纪律。

**不变量的数据归属分析（拆分的关键洞察）**：≤5 本上限、同书仅一笔在借，数据全在 `loans` 表内 → 归 loan-service 后**依然是本地事务**；跨服务的只剩"扣许可"一步。

**借阅链路 Saga 化（拆分时）**：调 book-service 原子扣减（`UPDATE ... SET available=available-1 WHERE id=? AND available>0`，即现有 CHECK 兜底逻辑前移）→ 本地插 loans（生成列唯一约束仍防重复）→ 插入失败补偿释放。user 行锁锚点随所有权迁移到 loan-service 库内（如 per-user 串行化行）——锁跟着数据所有权走。

**`GET /loans` 的 JOIN（拆分时二选一）**：API 组合（批量调 book-service）或借阅时存书名快照。现在保持 JOIN：快照列引入"书改名后显示旧名"的产品问题，为未到来的拆分预付代价违反 YAGNI。

**现在刻意不做**：事件/outbox、服务注册、分布式锁——README 拆分路径节论述。

## 13. README 大纲（简体中文）

1. 项目概述 + 功能列表
2. 快速开始（Docker 要求、`mvn test`、启动、seed 用户）
3. API 文档（端点、请求/响应示例、错误码表）
4. Assumptions（A1–A11 全文）
5. 设计决策：许可模型 / 并发与锁（三竞态）/ 状态派生 / 错误处理 / **MyBatis-Plus vs JPA 选型论证**
6. 演进路径：幂等方案、何时引入 Redis/Kafka、**限额/借期类别化配置**、微服务拆分

## 14. 取舍速查（弃选方案一览）

| 弃选 | 改用 | 理由 |
|---|---|---|
| `POST /books/{id}/borrow` | `POST /api/loans` | 资源创建语义 |
| PATCH /loans/{id} | `POST .../return` | 不暴露状态机给客户端 |
| 非本人 403 | 404 | 防 id 越权探测 |
| status 字段 | 时间戳派生 | 单一真相 |
| Spring Data JPA | MyBatis-Plus | 锁敏感场景 SQL 透明度（选型论证见 README） |
| PostgreSQL partial index | MySQL 生成列 + 唯一索引 | 数据库定为 MySQL，语义等效 |
| 原子 UPDATE 扣减 | 乐观锁 + 聚合方法 | 不绕过领域模型 |
| H2 内存库 | Testcontainers + MySQL | 约束/锁必须在真库验证；两套 DDL 真相不可接受 |
| 借阅时书名快照 | 拆分前保持 JOIN | YAGNI，避免元数据陈旧问题 |
| 外键约束 | 应用层归属校验 | 为拆分留路 |
| 重复归还 200 幂等 | 409 | 双归还是客户端 bug 信号 |
| 类别化限额/借期 | 固定 5 本 / 14 天（LoanPolicy 常量） | 需求仅单值，无第二规则信号（YAGNI）；README 演进节给方案 |
