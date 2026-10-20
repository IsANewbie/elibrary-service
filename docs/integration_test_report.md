# 集成测试报告

> 执行日期：2026/10/20 · 分支范围：`*IT.java`（maven-failsafe-plugin 的 integration-test/verify 阶段）

## 一、执行环境与方式

- **数据库**：外部 MySQL **8.4.11**，独立测试库 `elibrary_it`（`createDatabaseIfNotExist=true` 自动创建，与开发库隔离）。
- **schema 来源**：Flyway 迁移 V1–V3（init / seed books / add comments），3 条全部成功应用，schema 版本停在 v3。
- **无 Docker 依赖**：`AbstractIT` 检测到 `IT_DB_URL` 即走外部库直连，跳过 Testcontainers 容器生命周期。
- **命令**：`mvn verify`（先 surefire 跑单测，再 failsafe 跑集成测试）。

## 二、结果总览

| 层 | 测试数 | 通过 | 失败 | 错误 |
|---|---|---|---|---|
| 单元测试（surefire） | 60 | 60 | 0 | 0 |
| 集成测试（failsafe） | 17 | 17 | 0 | 0 |
| **合计** | **77** | **77** | **0** | **0** |

构建结果：**BUILD SUCCESS**，总耗时 42.5s。

## 三、集成测试明细

| 测试类 | 用例数 | 耗时 | 覆盖点 |
|---|---|---|---|
| `BookMapperIT` | 4 | 16.5s | 迁移+种子数据就位、CI collation 大小写不敏感搜索、分页拦截器真执行、乐观锁拦截器真执行（version +1 / 旧版本 0 行） |
| `LoanConcurrencyIT` | 5 | 5.7s | 四个并发竞态 + 唯一索引兜底约束（见下） |
| `LoanMapperIT` | 1 | 0.5s | JOIN 投影字段映射（标题/作者/时间戳）、在借计数与重复借查询 |
| `SecurityIT` | 5 | 3.1s | 无 token 401 problem+json、无效 token 401、login 免认证、全响应带 `X-Trace-Id`、JWT 主线端到端 |
| `LoginIT` | 2 | 0.3s | 种子用户登录成功、错误密码 401 + `INVALID_CREDENTIALS` |

### 并发竞态场景（LoanConcurrencyIT，真库验证）

| 场景 | 断言 |
|---|---|
| N 人抢最后名额 | 8 线程抢 1 个名额：恰好 1 成功、7 得 `BOOK_NO_LICENSE`，`available_licenses` 归零，绝不超卖 |
| 同用户并发双击 | 恰好 1 笔在借，另一线程得 `LOAN_DUPLICATE` |
| 第六笔并发借阅撞上限 | 6 线程各借一本书：5 成功、1 得 `LOAN_LIMIT_REACHED` |
| 并发双归还 | 恰好 1 成功、1 得 `LOAN_ALREADY_RETURNED`，名额不双放 |
| 唯一索引兜底 | 绕过服务层直接 INSERT 重复在借 → `DuplicateKeyException`，约束独立拦得住 |

## 四、发现的文档漂移

`CLAUDE.md` 声称"16 个集成测试"，实际为 **17**：`LoanConcurrencyIT` 除 4 个竞态场景外，另有独立的 `fallbackUniqueIndexBlocksDirectDuplicateInsert`（唯一索引兜底约束测试）未计入既有计数。

> 差异来源：该兜底测试验证的是「绕过应用层直接写库时，生成列唯一索引能否独立拦截重复在借」，属于约束层测试而非竞态测试，此前计数时可能只按「四竞态」口径统计。本次以 failsafe 实际执行数为准（17）。

## 五、测试数据副作用

集成测试是写库测试，结束后 `elibrary_it` 库状态：

- `books` = 6（种子书籍，`BookMapperIT.optimisticLockerReallyExecutes` 自愈还原名额）
- `users` = 10（种子 2 + 并发测试临时用户 8，用 `System.nanoTime()` 命名避免冲突）
- `loans` = 1（`LoanConcurrencyIT.raceEConcurrentReturnOneSucceedsOneConflicts` 遗留一笔在借，`@BeforeEach` 的 reset 只清 loans、还原 books，但不清理 users 表）

测试库隔离于开发库，不影响正常数据；如需反复运行，`elibrary_it` 可随时 drop 重建（Flyway 会自动重放）。
