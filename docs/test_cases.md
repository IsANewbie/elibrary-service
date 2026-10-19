# E-Library Service 提测测试用例

> 面向测试执行：黑盒视角，按模块组织；每条用例可直接复制的请求与明确预期。
> 约定：`$TOKEN` 为登录取得的 JWT；curl 示例为 Git Bash / WSL 语法，Postman/Apifox 同构。

## 1. 环境准备

**服务地址**：`http://localhost:8080`（部署后替换为实际地址）

**启动**（提测环境由开发提供，以下供自搭环境参考）：

```bash
docker run --name elibrary-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=elibrary -p 3306:3306 -d mysql:8.4
mvn spring-boot:run
```

**种子账号**：`alice` / `bob`，密码均为 `password123`

**种子书籍**（借阅/名额用例依赖这些数据）：

| id | 书名 | 类别 | 总名额 |
|---|---|---|---|
| 1 | 重构：改善既有代码的设计 | TECHNOLOGY | 3 |
| 2 | 领域驱动设计 | TECHNOLOGY | 2 |
| 3 | 代码整洁之道 | TECHNOLOGY | 1 |
| 4 | 三体 | LITERATURE | 5 |
| 5 | 百年孤独 | LITERATURE | 2 |
| 6 | 自然 | JOURNAL | 10 |

**数据重置**（每组用例执行前建议重置，SQL 在数据库执行）：

```sql
DELETE FROM loans;
UPDATE books SET available_licenses = total_licenses, version = 0;
```

> 注意：**不要**删除 users 表数据（alice/bob 为启动时注入，删空后需重启服务才会重建）。

**通用预期（全部接口适用）**：
- 错误响应统一 RFC 7807：`Content-Type: application/problem+json`，含 `title`/`status`/`detail`，业务错误另有 `errorCode` 字段，body 校验失败附 `fields` 明细
- 受保护接口不带/带无效 token 一律 401（Security 层，无业务 errorCode，响应头含 `WWW-Authenticate: Bearer`）

## 2. 认证模块（AUTH）

| 编号 | 优先级 | 用例 | 步骤 | 预期 |
|---|---|---|---|---|
| AUTH-01 | P0 | 正常登录 | `curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"alice","password":"password123"}'` | 200；`{"token":"eyJ..."}`，token 可用于后续请求 |
| AUTH-02 | P0 | 密码错误 | 同上，password 改为 `wrong` | 401；`errorCode=INVALID_CREDENTIALS` |
| AUTH-03 | P1 | 用户不存在 | username 改为 `nobody` | 401；`errorCode=INVALID_CREDENTIALS`，**响应与密码错误完全一致**（防用户名枚举，对比两者 body 应无差别） |
| AUTH-04 | P1 | 缺字段 | body 只传 `{"username":"alice"}` | 400；`errorCode=VALIDATION_FAILED`；`fields.password` 存在 |
| AUTH-05 | P2 | 错误方法 | `curl -s -o /dev/null -w "%{http_code}" $BASE/api/auth/login`（GET） | 405（不是 401、不是 500） |
| AUTH-06 | P0 | 无 token 访问受保护接口 | `curl -s $BASE/api/books` | 401；problem+json；响应头含 `WWW-Authenticate: Bearer`；detail 中文正常显示（无乱码问号） |
| AUTH-07 | P0 | 篡改 token | AUTH-01 的 token 末尾追加字符后携带 | 401 |
| AUTH-08 | P1 | 非 JWT 格式 | `Authorization: Bearer not-a-jwt` | 401（不是 500） |
| AUTH-09 | P2 | 空 Bearer | `Authorization: Bearer `（空值） | 401 |
| AUTH-10 | P2 | token 过期 | 拿开发提供的已过期 token（或 JWT_SECRET 已知时自签 exp 过去的 token） | 401 |

## 3. 浏览书籍（LIST）

统一携带 `-H "Authorization: Bearer $TOKEN"`。

| 编号 | 优先级 | 用例 | 步骤 | 预期 |
|---|---|---|---|---|
| LIST-01 | P0 | 默认列表 | `GET /api/books?page=1&size=10` | 200；`total=6`、`content` 6 项；每项含 `id/title/author/category/availableLicenses`，**不含** `description`（列表精简，详情才有） |
| LIST-02 | P0 | 分页 | `page=1&size=2` → 再 `page=2&size=2` … 至 `page=4` | 第 1-3 页各 2 项，第 4 页 0 项；`total=6` 恒定；`page/size` 回显请求值 |
| LIST-03 | P1 | keyword 命中书名 | `?keyword=三体&page=1&size=10` | 200；恰好 1 项（三体） |
| LIST-04 | P1 | keyword 命中作者（大小写不敏感） | `?keyword=martin fowler`（全小写） | 200；恰好 1 项（重构） |
| LIST-05 | P1 | category 过滤 | `?category=TECHNOLOGY&page=1&size=10` | 200；恰好 3 项（id 1/2/3） |
| LIST-06 | P2 | 组合过滤 | `?keyword=设计&category=TECHNOLOGY` | 200；命中"重构：改善既有代码的设计"与"领域驱动设计"共 2 项 |
| LIST-07 | P2 | keyword 无命中 | `?keyword=不存在的书` | 200；`content=[]`、`total=0`（**不是 404**） |
| LIST-08 | P0 | 页码非法不钳位 | `page=0&size=10` | 400；`errorCode=VALIDATION_FAILED`（显式报错，不悄悄按第 1 页处理） |
| LIST-09 | P1 | size 超上限 | `size=101` | 400；`errorCode=VALIDATION_FAILED` |
| LIST-10 | P1 | 参数非数字 | `page=abc` | 400；`errorCode=VALIDATION_FAILED` |
| LIST-11 | P2 | 缺必填分页参数 | 不带 `page`/`size` | 400；`errorCode=VALIDATION_FAILED` |
| LIST-12 | P2 | 页码超范围 | `page=999&size=10` | 200；`content=[]`、`total=6`（空页不是错误） |

## 4. 书籍详情（DETAIL）

| 编号 | 优先级 | 用例 | 步骤 | 预期 |
|---|---|---|---|---|
| DETAIL-01 | P0 | 详情字段完整 | `GET /api/books/1` | 200；含 `description`（列表没有）与 `borrowedByYou=false`（alice 未借时） |
| DETAIL-02 | P0 | borrowedByYou 联动 | alice 借书 1（见 BORROW-01）后再看详情 | `borrowedByYou=true`，`availableLicenses` 较借阅前 -1 |
| DETAIL-03 | P1 | 归还后回退 | 归还该书（见 RETURN-01）后再看详情 | `borrowedByYou=false`，`availableLicenses` +1 恢复 |
| DETAIL-04 | P0 | 书不存在 | `GET /api/books/999` | 404；`errorCode=BOOK_NOT_FOUND` |
| DETAIL-05 | P2 | id 非数字 | `GET /api/books/abc` | 400；`errorCode=VALIDATION_FAILED` |

## 5. 借阅（BORROW）

| 编号 | 优先级 | 用例 | 步骤 | 预期 |
|---|---|---|---|---|
| BORROW-01 | P0 | 正常借阅 | `curl -i -X POST $BASE/api/loans -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"bookId":1}'` | **201**；响应头 `Location: /api/loans/{id}`；body 含 `bookId=1`、`borrowedAt`、`dueAt`，且 **`dueAt - borrowedAt` 恰好 14 天**；`overdue=false`。注意：借阅响应的 `title/author` 为 null 属预期（创建时聚合无书名冗余），在借列表/归还响应中正常返回 |
| BORROW-02 | P0 | 重复借同书 | 紧接 BORROW-01 重复同一请求 | 409；`errorCode=LOAN_DUPLICATE` |
| BORROW-03 | P0 | 名额耗尽 | alice 借书 3（名额 1）成功；bob 登录后再借书 3 | bob 得 409；`errorCode=BOOK_NO_LICENSE` |
| BORROW-04 | P0 | 在借上限 | 重置数据后 alice 依次借书 1/2/3/4/5（各一次，均 201），再借书 6 | 第 6 次请求 409；`errorCode=LOAN_LIMIT_REACHED`；前 5 次 201 |
| BORROW-05 | P0 | 书不存在 | `{"bookId":999}` | 404；`errorCode=BOOK_NOT_FOUND` |
| BORROW-06 | P1 | bookId 缺失 | body `{}` | 400；`errorCode=VALIDATION_FAILED`；`fields.bookId` 存在 |
| BORROW-07 | P2 | bookId 非数字 | `{"bookId":"abc"}` | 400；`errorCode=VALIDATION_FAILED` |
| BORROW-08 | P1 | 归还后可再借 | alice 借书 1 → 归还 → 再借书 1 | 三步分别 201 → 200 → 201（唯一约束只挡"在借"状态） |
| BORROW-09 | P2 | 不同用户互不干扰 | alice 在借 3 本时，bob 借任意未借书 | 201（上限按用户各自计数） |

## 6. 归还（RETURN）

| 编号 | 优先级 | 用例 | 步骤 | 预期 |
|---|---|---|---|---|
| RETURN-01 | P0 | 正常归还 | alice 借书 1 得 `loanId` 后：`curl -X POST $BASE/api/loans/{loanId}/return -H "Authorization: Bearer $TOKEN"` | 200；`returnedAt` 非空；`overdue=false`；`title/author` 有值；书 1 详情 `availableLicenses` +1 |
| RETURN-02 | P0 | 重复归还 | 紧接 RETURN-01 再发一次 | 409；`errorCode=LOAN_ALREADY_RETURNED`；**名额不双放**（详情 `availableLicenses` 保持不变） |
| RETURN-03 | P0 | 归还他人借阅（防越权探测） | alice 借得 `loanId`；用 **bob 的 token** 调归还 | **404**（不是 403）；`errorCode=LOAN_NOT_FOUND`——与"记录不存在"响应一致，防止探测他人 loan id |
| RETURN-04 | P1 | loan 不存在 | `POST /api/loans/99999/return` | 404；`errorCode=LOAN_NOT_FOUND` |
| RETURN-05 | P2 | 归还后从在借列表消失 | 借 2 本 → 归还其中 1 本 → GET /api/loans | 列表剩 1 项 |

## 7. 当前在借列表（LOANS）

| 编号 | 优先级 | 用例 | 步骤 | 预期 |
|---|---|---|---|---|
| LOANS-01 | P0 | 列表内容 | alice 借书 1、书 4 后 `GET /api/loans` | 200；恰好 2 项；每项含 `bookId/title/author/borrowedAt/dueAt/returnedAt=null/overdue` |
| LOANS-02 | P0 | 各用户只见自己 | bob 同时查看自己的列表 | bob 视角不含 alice 的借阅（接口无 userId 参数，身份由 token 决定） |
| LOANS-03 | P1 | 逾期派生 | DB 造数：`UPDATE loans SET due_at = DATE_SUB(NOW(6), INTERVAL 1 SECOND) WHERE user_id = {aliceId};` 后刷新列表 | 该项 `overdue=true`；恢复 `due_at` 为未来时间后 `overdue=false` |
| LOANS-04 | P2 | 到期时刻边界 | DB 造数将 `due_at` 设为**恰好等于当前时刻**后刷新 | `overdue=false`（严格大于才算逾期：`now > dueAt`） |
| LOANS-05 | P2 | 空列表 | 重置数据后查看 | 200；`[]` |

## 8. 并发用例（CONC，可选）

> 环境不便施压时，以下场景已由自动化集成测试 `LoanConcurrencyIT` 覆盖（`mvn verify`），可要求开发提供执行记录替代。手工简化版如下。

| 编号 | 优先级 | 用例 | 步骤 | 预期 |
|---|---|---|---|---|
| CONC-01 | P1 | 同用户并发双击 | 两个终端**同时**以 alice 的 token 发同一借阅请求（书 1） | 恰好一个 201、一个 409 `LOAN_DUPLICATE`；库中该书在借记录恰好 1 条 |
| CONC-02 | P1 | 两人抢最后名额 | alice 与 bob **同时**借书 3（名额 1） | 恰好一个 201、一个 409 `BOOK_NO_LICENSE`；详情 `availableLicenses=0`，绝不超卖 |
| CONC-03 | P2 | 并发双归还 | 两个终端**同时**归还 alice 的同一 loan | 恰好一个 200、一个 409 `LOAN_ALREADY_RETURNED`；名额恰好 +1 |

施压技巧（Git Bash，& 后台并发后等待）：

```bash
for i in 1 2 3 4 5; do curl -s -o /dev/null -w "%{http_code}\n" -X POST $BASE/api/loans \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"bookId":1}' & done; wait
```

## 9. 横切断言（CROSS）

| 编号 | 优先级 | 用例 | 步骤 | 预期 |
|---|---|---|---|---|
| CROSS-01 | P1 | 错误格式统一 | 抽查任意 3 个错误响应 | 均为 `application/problem+json`；含 `title/status/detail`；业务错误含 `errorCode` |
| CROSS-02 | P1 | 未映射路由 | `GET /api/no-such-path`（带 token） | 404（不是 500） |
| CROSS-03 | P2 | 中文 detail 无乱码 | AUTH-02 等含中文 detail 的响应 | UTF-8 正常显示（无 `?` 乱码） |

## 附：错误码速查

| HTTP | errorCode | 触发场景速记 |
|---|---|---|
| 400 | VALIDATION_FAILED | 参数缺失/非法/越界（带 `fields` 明细） |
| 401 | INVALID_CREDENTIALS | login 凭证错误（仅此端点） |
| 401 | —（无 errorCode） | 无/无效/过期 token |
| 404 | BOOK_NOT_FOUND | 书不存在 |
| 404 | LOAN_NOT_FOUND | loan 不存在 **或 非本人 loan** |
| 409 | BOOK_NO_LICENSE | 名额耗尽 |
| 409 | LOAN_LIMIT_REACHED | 在借 ≥ 5 本 |
| 409 | LOAN_DUPLICATE | 同书重复在借 |
| 409 | LOAN_ALREADY_RETURNED | 重复归还 |
| 409 | CONCURRENT_MODIFICATION | 并发修改冲突（重试后仍失败） |

**预期内的"看似怪异"行为**（勿提 bug）：借阅 201 响应的 `title/author` 为 null（创建视图无书名冗余，列表/归还视图才有）；`GET /api/loans` 无 userId 查询参数（身份由 token 决定，防 IDOR）。
