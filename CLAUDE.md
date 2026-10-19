# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# E-Library Service — 工程记忆

## 项目与范围
电子图书馆后端，5 项功能：浏览书籍、书籍详情、借阅、归还、查看当前借阅。
范围外：DRM/内容分发、Hold/预约、期刊单独建模、通知、逾期罚金。
质量、设计取舍、可维护性优先于功能完整；未明确假设写进 README。

## 当前阶段
需求分析 v2 与设计推演 v2 已定稿（`docs/requirements_analysis.md` + `docs/design_walkthrough.md`），代码未开始（等待确认后再实现）。实现顺序：骨架 → 领域模型 → Security+JWT → REST API → 测试 → README。
实现借阅、异常处理、API 前先读 `docs/design_walkthrough.md`（错误码全景与竞态分析）。
仓库尚无构建文件、尚未 git init（用户暂缓）；骨架建立后在此补充构建、测试、跑单个测试的命令。

## 技术栈
Java + Spring Boot（Web / Security / Validation）+ **MyBatis-Plus**（弃 Spring Data JPA，锁敏感场景 SQL 透明优先，README 需论证）。
**MySQL 8**（CHECK 需 ≥8.0.16；无 partial index → 生成列 + 唯一索引等效）；Flyway schema 即代码；集成测试 Testcontainers。
JWT 无状态认证（jjwt HS256，种子用户 + login，不做注册）。测试 JUnit 5 + Mockito + @WebMvcTest + Testcontainers 集成。

## API 一览（决策已定，推演细节见 docs/design_walkthrough.md）
- `POST /api/auth/login` — 种子用户签发 JWT；失败 401 INVALID_CREDENTIALS
- `GET /api/books` — keyword/category/page/size；offset 分页（页码非法 400，不钳位）；列表带 availableLicenses
- `GET /api/books/{id}` — 含 description 与 borrowedByYou
- `POST /api/loans` body `{bookId}` — 资源创建语义（弃 `POST /books/{id}/borrow`）
- `POST /api/loans/{id}/return` — 服务端命令（弃 PATCH，不暴露状态机）
- `GET /api/loans` — 无 userId 参数，当前用户由 JWT 解出；overdue 为派生字段

## 不可违背的设计约束
- 数字内容 = 并发许可（license），非实体副本；借期 14 天，每用户最多 5 本在借。
- 借阅锁顺序固定：先 user 行悲观锁，后 Book 乐观锁 + DB 兜底；乐观锁冲突自动重试一次。
- 状态不落库：active/returned/overdue 全部由时间戳派生；overdue 时间比较唯一入口 `LoanPolicy.isOverdue`（Loan.isOverdue 与列表派生共用，防公式双写）。
- 认证用 Spring Security + JWT，无状态；GET /loans 的当前用户从 JWT 解出。
- 非本人 loan 返回 404（防越权探测）；重复归还 409。
- 错误响应统一 RFC 7807 problem+json + 业务 errorCode；404/409/400 映射集中一处。
- 测试：领域 JUnit + Service Mockito + @WebMvcTest + Testcontainers 集成（四竞态场景）；每个领域异常路径至少一个测试。
- 刻意不引入 Redis/Kafka/gRPC；Idempotency-Key 不做；限额/借期保持固定值（类别化配置只进 README 演进节）。

## 代码组织
package-by-feature：`book/`、`loan/`、`user/`、`common/`，各含 api/application/domain/mapper。
domain 充血，业务规则在聚合内；application 只做事务、锁顺序、编排；DTO 用 record，领域对象不出 web 层。
**跨 feature 红线**：写侧——loan 禁止注入 BookMapper/UserMapper 或跨包改数据，只经 `book/api/BookFacade` 与 `user/api/UserFacade.lockById`（同事务参与，不得 REQUIRES_NEW），modular monolith 抽取路径；读侧——mapper 层允许跨表 JOIN 投影（声明的拆分债）。
MP Wrapper 只出现在查询组装处，不出现在业务判断处；JOIN 投影允许 mapper 层视图 POJO（MyBatis 对 record 构造器映射不完善），web 层 DTO 仍用 record。
数据库表复数、列 snake_case，索引 `idx_` / 唯一索引 `uq_`；无外键（为拆分留路，归属校验在应用层）。

## 协作与规范
中文交流，技术术语保留英文；先设计打磨确认再写代码，不跳步；所有决策要能讲出取舍。
注释用中文，只写”为什么”，不复述代码。类头 Javadoc 三件套（主代码与测试类都要）：
`/** 一行中文职责描述 · @author link · @date yyyy/MM/dd（文件创建日期） */`。
测试方法名用英文行为式驼峰命名（如 `borrowDecrementsAvailableLicenses`），不加 @DisplayName；可读性靠命名 + 方法内中文注释。
异常：业务规则违反抛领域异常，继承 `DomainException`，带稳定 errorCode。
Git 用 Conventional Commits，一行说清意图。

## 文档红线
交付物（代码/README/注释）不得提及项目外背景或开发环境信息。
README 在工程根目录；`docs/` 为过程文档，随仓库提交，内容同样不得提及项目外背景。
