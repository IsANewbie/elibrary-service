CREATE TABLE users (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64)  NOT NULL,
    password VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE books (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    title              VARCHAR(255) NOT NULL,
    author             VARCHAR(128) NOT NULL,
    category           VARCHAR(64)  NOT NULL,
    description        TEXT,
    total_licenses     INT NOT NULL,
    available_licenses INT NOT NULL,
    version            INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_books_licenses CHECK (available_licenses >= 0 AND available_licenses <= total_licenses),
    KEY idx_books_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

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
    KEY idx_loans_user_active (user_id, returned_at),
    -- 详情页 borrowedByYou 查询（book_id + user_id 前导）的支撑索引
    KEY idx_loans_book_user (book_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
