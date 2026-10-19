# E-Library Service

电子图书馆后端服务：浏览书籍、查询详情、借阅、归还、查看当前在借。

## 功能

| 功能 | 端点 |
|---|---|
| 登录签发 JWT | `POST /api/auth/login` |
| 浏览书籍（关键词/类别/分页） | `GET /api/books` |
| 书籍详情 | `GET /api/books/{id}` |
| 借阅 | `POST /api/loans` |
| 归还 | `POST /api/loans/{id}/return` |
| 查看当前在借 | `GET /api/loans` |

## 快速开始

**环境要求**：JDK 21、Maven 3.9+、Docker（跑集成测试）。数据库要求 **MySQL ≥ 8.0.16**（CHECK 约束生效的最低版本），示例与测试镜像统一钉在 8.4 LTS 以保证可复现。

```bash
# 1. 启动 MySQL
docker run --name elibrary-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=elibrary -p 3306:3306 -d mysql:8.4

# 2. 全量验证（单测 + Testcontainers 集成测试）
mvn verify

# 3. 启动（schema 与种子书籍由 Flyway 迁移，种子用户启动时注入）
mvn spring-boot:run
```

种子账号：`alice` / `bob`，密码均为 `password123`。

> **部署注意**：`JWT_SECRET` 仅为本地默认值，任何真实部署必须以环境变量注入——对称密钥一旦公开即全量冒充。token 时钟未设偏移容差，单实例无影响，多实例部署需评估 `clockSkewSeconds`。

```bash
# 登录拿 token
curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}'
# → {"token":"eyJ..."}

# 浏览（page 从 1 起，size 1-100）
curl -s "localhost:8080/api/books?page=1&size=3" -H "Authorization: Bearer <token>"
# → {"content":[{"id":1,"title":"重构：改善既有代码的设计","author":"Martin Fowler","category":"TECHNOLOGY","availableLicenses":3},...],"total":6,"page":1,"size":3}

# 借阅
curl -s -X POST localhost:8080/api/loans -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"bookId":1}'
# → 201 Created，Location: /api/loans/{id}
```

## API 与错误契约

错误响应统一 RFC 7807 `application/problem+json`，扩展 `errorCode` 字段；body 校验失败附 `fields` 字段明细。

| HTTP | errorCode | 场景 |
|---|---|---|
| 401 | INVALID_CREDENTIALS | login 用户名/密码错误 |
| 401 | —（无业务 errorCode） | 无效/过期 Bearer token（Security 层，响应带 `WWW-Authenticate: Bearer`） |
| 400 | VALIDATION_FAILED | 参数校验，带字段明细 |
| 404 | BOOK_NOT_FOUND / LOAN_NOT_FOUND | 书籍/借阅不存在；**非本人 loan 同样 404**（防 id 越权探测） |
| 409 | BOOK_NO_LICENSE | 可借名额耗尽 |
| 409 | LOAN_LIMIT_REACHED | 在借达上限（5 本） |
| 409 | LOAN_DUPLICATE | 重复借阅同书（含唯一索引兜底命中） |
| 409 | LOAN_ALREADY_RETURNED | 重复归还 |
| 409 | CONCURRENT_MODIFICATION | 乐观锁冲突（重试后仍失败） |

无角色模型，403 不可达（`anyRequest().authenticated()` 下已认证用户无被拒路径）——为有意取舍；未来引入角色时需同步补 problem+json 的 AccessDeniedHandler。

## 假设（Assumptions）

需求未明确处，按以下假设实现：

- **A1** 数字内容 = 并发许可（availableLicenses），非实体副本——数字内容可无限复制，"借"的本质是占用一个并发名额
- **A2** 借期 14 天；overdue 由时间戳派生（`now > dueAt`，严格大于），不落库
- **A3** 每用户同时在借 ≤ 5 本；**A4** 同用户同书同时仅一笔在借
- **A5** 阅读/内容分发出范围（概述提及"阅读"但功能列表没有——是平台愿景而非本期范围）
- **A6** 电子书与期刊不单独建模，category 字段区分
- **A7** 检索 = keyword（title/author 模糊）+ category 过滤 + 分页（offset 分页：目录量级深翻页代价可忽略）
- **A8** 认证 JWT 无状态（HS256，24h）；种子用户 + login，不做注册/改密（账户体系不属于图书馆域）
- **A9** MySQL 8（CHECK 需 ≥ 8.0.16）
- **A10** 无通知、预约/Hold、逾期罚金、管理端、软删除
- **A11** 幂等键不做（见演进路径）；无外键约束（归属校验在应用层，为拆分留路）

## 设计决策

### 并发与锁（核心）

借阅校验链顺序固定，同一事务内：

```
① 认证（JWT → userId）          ② 入参校验（bookId 非空）
③ SELECT user FOR UPDATE        ← 锁顺序第一步：串行化同用户操作
④ 在借数 < 5                    → 409 LOAN_LIMIT_REACHED
⑤ 未借过此书                    → 409 LOAN_DUPLICATE
⑥ Book.borrow()（@Version 乐观锁）→ 409 BOOK_NO_LICENSE
⑦ INSERT loans（唯一索引兜底）
```

**锁组合**：user 行悲观锁（保正确性，锁冷行无争用）+ Book 乐观锁（保吞吐，读多写少）+ DB 约束兜底（防应用 bug）。归还不取 user 锁只碰 Book，依赖方向 user→book 单向无环，无死锁。

三个竞态的解法（并发集成测试在真 MySQL 上逐一验证）：

| 竞态 | 解法 |
|---|---|
| N 人抢最后名额 | 乐观锁一方失败 + `CHECK (available_licenses >= 0)` 兜底，绝不超卖 |
| 乐观锁假冲突（名额充足但 version 撞） | 自动重试一次；**冲突后重载必须当前读**（`FOR UPDATE`）——REPEATABLE READ 下普通 select 读事务快照，拿到过期 version 重试必然再失败 |
| 同用户并发双击 | user 行锁串行化 + 生成列唯一索引兜底 |
| 并发双归还 | 条件 UPDATE 状态机：`UPDATE ... WHERE returned_at IS NULL`，恰好一个事务命中，名额不双放 |

### MySQL 适配

- **生成列等效 partial unique index**：`active_book_id BIGINT GENERATED ALWAYS AS (IF(returned_at IS NULL, book_id, NULL)) STORED` + `UNIQUE(user_id, active_book_id)`——MySQL 唯一索引忽略 NULL，在借行按 (user, book) 唯一、已归还行不受限，语义与 PostgreSQL 的 `UNIQUE ... WHERE returned_at IS NULL` 完全等价
- **CHECK 防超卖**：`CHECK (available_licenses >= 0 AND available_licenses <= total_licenses)`（8.0.16 起强制执行）
- **CI collation**：`utf8mb4_0900_ai_ci` 显式 pin（DDL 不依赖 server 默认值），keyword 搜索免 `lower()`
- 索引：`(user_id, returned_at)` 支撑在借计数/列表，`(book_id, user_id)` 支撑详情页 borrowedByYou，均与查询形态对齐

### 状态不落库

```
active := returnedAt == null
overdue := active && now > dueAt
```

不存 status enum：enum 与时间戳两份真相必然漂移。整个系统唯一持久化的状态变化就是 `returnedAt` 一个时间戳。overdue 判定全系统唯一入口 `LoanPolicy.isOverdue`（聚合方法与列表派生共用）。

### MyBatis-Plus vs Spring Data JPA

选 MyBatis-Plus，理由：本项目的核心难点是并发正确性——`FOR UPDATE`、生成列唯一约束、条件 UPDATE 状态机、精确的 UPDATE 影响行数判断，这些在 JPA 里要经 `@Lock`/`@Version` 等抽象间接表达，在 MP 里就是一行行显式 SQL，**锁语义一目了然**。

何时会反向选择 JPA：对象图复杂、跨聚合导航多、写少读多的 CRUD 型业务——JPA 的脏检查与关系映射收益更大，锁透明度的损失可接受。

### API 语义三处取舍

- 借阅用 `POST /api/loans`（创建借阅资源）而非 `POST /books/{id}/borrow`（动作型 URL）
- 归还用 `POST /api/loans/{id}/return`（服务端命令）而非 `PATCH /api/loans/{id}`（PATCH 是客户端设值，会暴露状态机）
- 非本人 loan 返回 **404** 而非 403——403 等于承认资源存在，可被用于越权探测

### 错误处理

RFC 7807 + 稳定 errorCode；`errorCode → HTTP` 映射集中一处（`GlobalExceptionHandler`），领域层不感知 HTTP。兜底 handler 对 Spring 协议异常（405/415 等）按原生状态返回，不吞成 500。

## 测试策略

| 层 | 工具 | 覆盖 |
|---|---|---|
| 领域 | 纯 JUnit | 名额边界、重复归还、逾期派生边界（now == dueAt 不算逾期） |
| Service | Mockito | 校验链顺序（InOrder）、异常翻译、乐观锁重试 |
| Web | @WebMvcTest | 7807 序列化、状态码映射、参数校验 400 |
| 集成 | Testcontainers（MySQL 8.4） | 迁移/种子/collation/分页/乐观锁冒烟、JOIN 投影映射、JWT 主线端到端、**四竞态 + 兜底约束** |

集成测试支持外部数据库直连（`IT_DB_URL`/`IT_DB_USER`/`IT_DB_PASSWORD` 环境变量），无 Docker 环境也可跑真库验证；不设则 Testcontainers 起临时容器。

## 演进路径

- **幂等**：网络重试目前被 409 LOAN_DUPLICATE 自然挡住（体验可接受）。转账类业务必须做严格幂等：`Idempotency-Key` 头 + 请求指纹表（key、body hash、响应快照），命中即重放
- **Redis**：同用户借阅的串行化目前靠 user 行锁，单库下足够；多实例 + 热点用户场景可上分布式锁，但先测量——行锁锁的是冷行，争用本就罕见
- **Kafka**：逾期提醒、借阅事件审计需要时引入（事件发布与业务解耦）；当前无通知需求，不引入
- **限额/借期类别化**：需求只给了单一数字（5 本/14 天），保持 LoanPolicy 常量。需要时可换 `@ConfigurationProperties`（default + by-category map，未配置类别回退 default）——注意校验链需重排（书先加载才能按类别检查）、在借计数变按类别 JOIN、Saga 化时 reserve 响应需带回 category
- **微服务拆分**：package-by-feature + 写侧 Facade（BookFacade/UserFacade）已就绪，拆分时把 facade 实现换成 REST 客户端，LoanService 不改。关键洞察：≤5 本上限、同书仅一笔在借的数据全在 loans 表内，拆分后归 loan-service 仍是本地事务；跨服务的只剩"扣许可"一步（Saga：原子扣减 → 本地插 loans → 失败补偿释放）。`GET /loans` 的 JOIN 改 API 组合或书名快照，拆分时再定
