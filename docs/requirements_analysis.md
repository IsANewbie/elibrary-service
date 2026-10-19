# E-Library Service — 需求分析（v2 定稿）

> 输入：`Backend_Take_Home_E_Library_Service Chinese.docx`（需求原文）。
> 本文是审题记录：显式需求、隐含需求推理、假设清单、技术选型依据。
> 过程文档，不随项目交付；最终对外交付物是工程根目录 README.md。

## 0. 输入与解读姿态

| 输入 | 实质内容 | 对本项目的意义 |
|---|---|---|
| 需求文档（繁中） | 概述一句 + 5 行功能 + 6 项技术考察维度 + 交付物（源码、README）+ 备注 | 备注明说：**重思考过程与设计取舍、轻功能完整**，无唯一正确答案，未明确处可自行合理假设 |

## 1. 功能需求（显式，全部来自原文）

F1 浏览书籍 · F2 查询书籍详细资料 · F3 借阅书籍 · F4 归还书籍 · F5 查看目前已借阅的书籍

原文**未出现**的词：认证、搜索、分页、数据库、借期、数量上限——全部走"合理假设"通道，逐条显式化（见 §3）。

## 2. 隐含需求（从措辞推理）

| # | 原文线索 | 推导 |
|---|---|---|
| I1 | "查看**目前已**借閱的書籍" | 存在用户概念 + 借阅有当前/历史之分 → **认证是隐含需求**，非凭空加戏 |
| I2 | "歸還書籍" 是独立功能 | 借阅是可逆状态机：active → returned |
| I3 | 概述有"閱讀"，功能列表没有 | 阅读是平台愿景而非本次范围（假设 A5） |
| I4 | "電子書及期刊"，5 个功能对二者一视同仁 | 期刊不单独建模，类型字段区分（假设 A6） |
| I5 | 数字内容的"借"与物理书本质不同 | **借 = 占用一个并发许可**，实体副本语义是错误模型（假设 A1） |
| I6 | "瀏覽" 未定义 | keyword + category + 分页（假设 A7） |

## 3. 假设清单（进 README Assumptions 节）

- **A1** 数字内容 = 并发许可（availableLicenses），非实体副本
- **A2** 借期 14 天；overdue 由时间戳派生，不落库
- **A3** 每用户同时在借 ≤ 5；**A4** 同用户同书同时仅一笔在借
- **A5** 阅读/内容分发出范围；**A6** 期刊用 category 区分，不单独建模
- **A7** 检索 = keyword（title/author 模糊）+ category 过滤 + 分页
- **A8** JWT 无状态认证；种子用户 + `POST /api/auth/login` 签发 token；不做注册/改密（账户体系不属图书馆域）
- **A9** MySQL 8（CHECK 约束需 ≥ 8.0.16）+ MyBatis-Plus
- **A10** 无通知、预约/Hold、逾期罚金、管理端、软删除
- **A11** 幂等键不做，README 单列方案（转账类业务必须做）

## 4. 非功能需求（6 项技术考察维度 → 落点）

| 考察维度 | 落点 |
|---|---|
| 应用程式架构 | 单体分层 + package-by-feature；跨 feature **写侧**只经 application 层接口（BookFacade / UserFacade），读侧 JOIN 投影为声明的拆分债；拆分路径 README 论述 |
| 領域模型設計 | 充血聚合：业务规则在 Book/Loan 实体内；状态由时间戳派生不落库 |
| API 設計 | 资源语义端点 + RFC 7807 错误契约 + 稳定 errorCode |
| 程式碼組織 | `book/ loan/ user/ common/`，各含 api/application/domain；DTO 用 record，领域对象不出 web 层 |
| 錯誤處理 | DomainException 体系（继承 + 稳定 errorCode）；401/400/404/409 映射集中一处 |
| 可維護性 | 四层测试（领域/Service/Web/集成）+ Flyway schema 即代码 + Conventional Commits |

## 5. 技术选型与依据

| 选型 | 依据 |
|---|---|
| Spring Boot + MVC + Security + Validation | Spring 生态主流栈，需求文档 6 项技术考察维度（应用架构/API/错误处理等）均落于其上 |
| **MyBatis-Plus**（弃 Spring Data JPA） | 锁敏感场景 SQL 透明度优先：`FOR UPDATE`、生成列唯一约束、精确 UPDATE 全部显式可见，正对题目并发核心。Spring 生态默认 JPA，此为主动取舍，论证与对照（@Version/@Lock/EntityGraph）写进 README |
| MySQL 8 + Flyway + Testcontainers | 约束/索引/生成列全进版本化 DDL；集成测试在真库验证锁与约束 |
| JWT（jjwt，HS256） | 隐含需求 I1；无状态天然利好未来微服务拆分（本地验签） |
| 刻意不引入：Redis、Kafka、gRPC、Idempotency-Key | 单体规模下无收益；README 各自写清"何时该引入"——这本身是取舍展示 |

## 6. 范围边界

**做**：5 个功能端点 + login，约 6 类 SQL 场景，四层测试。
**不做**（每条 README 讲清何时该做）：阅读/内容分发、注册/改密、Hold/预约、逾期罚金、通知、软删除、幂等键、管理端、外键约束（为拆分留路，归属校验在应用层）、限额/借期类别化配置（README 演进节给方案）。

## 7. 微服务演进就绪度（结论，细节见 design_walkthrough.md §12）

已就绪：package-by-feature 物理边界、JWT 本地验签、领域规则无框架耦合、DTO/7807 契约显式、写侧经 facade 的访问纪律。
拆分时才动：借阅本地事务 → Saga（不变量数据归属分析表明跨服务的只剩"扣许可"一步）；`GET /loans` JOIN → API 组合或快照（拆分时再定，现在保持 JOIN）。
