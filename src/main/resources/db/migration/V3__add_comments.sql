-- 表与字段注释：数据字典随 schema 版本化（V1 已发布，注释走增量迁移）

ALTER TABLE users COMMENT = '用户：借阅归属方与行锁锚点（非账户体系，仅种子数据）';
ALTER TABLE users
    MODIFY id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY username VARCHAR(64)  NOT NULL COMMENT '用户名（唯一，utf8mb4 CI 大小写不敏感）',
    MODIFY password VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希（种子用户启动时注入）';

ALTER TABLE books COMMENT = '书籍：数字内容 = 并发许可，非实体副本';
ALTER TABLE books
    MODIFY id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY title              VARCHAR(255)  NOT NULL COMMENT '书名（keyword 匹配列之一）',
    MODIFY author             VARCHAR(128)  NOT NULL COMMENT '作者（keyword 匹配列之一）',
    MODIFY category           VARCHAR(64)   NOT NULL COMMENT '类别（TECHNOLOGY/LITERATURE/JOURNAL，电子书与期刊不单独建模）',
    MODIFY description        TEXT          NULL COMMENT '简介（仅详情接口返回，列表精简）',
    MODIFY total_licenses     INT           NOT NULL COMMENT '总许可数（并发借阅上限）',
    MODIFY available_licenses INT           NOT NULL COMMENT '当前可借名额（借-1/还+1，CHECK 防超卖）',
    MODIFY version            INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（@Version，冲突自动重试一次）';

ALTER TABLE loans COMMENT = '借阅：状态不落库，active/overdue 全由时间戳派生';
ALTER TABLE loans
    MODIFY id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY user_id       BIGINT      NOT NULL COMMENT '借阅用户 id（无外键，归属校验在应用层）',
    MODIFY book_id       BIGINT      NOT NULL COMMENT '书籍 id（无外键，归属校验在应用层）',
    MODIFY borrowed_at   DATETIME(6) NOT NULL COMMENT '借出时间（应用层 UTC）',
    MODIFY due_at        DATETIME(6) NOT NULL COMMENT '应还时间 = borrowed_at + 14 天；overdue 判定：now > due_at 严格大于',
    MODIFY returned_at   DATETIME(6) NULL COMMENT '归还时间；NULL=在借——全系统唯一持久化状态变化',
    MODIFY active_book_id BIGINT     GENERATED ALWAYS AS (IF(returned_at IS NULL, book_id, NULL)) STORED COMMENT '生成列（只读）：在借行=book_id/已归还=NULL，配合唯一索引等效 partial unique index';

