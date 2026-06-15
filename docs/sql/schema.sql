CREATE DATABASE IF NOT EXISTS whx_bill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE whx_bill;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    email VARCHAR(128),
    phone VARCHAR(32),
    avatar_url VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    user_type TINYINT NOT NULL DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    permission_name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(20) NOT NULL,
    path VARCHAR(255),
    component VARCHAR(255),
    icon VARCHAR(64),
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_user_role(user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_role_permission(role_id, permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
);

CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type VARCHAR(64) NOT NULL,
    dict_label VARCHAR(64) NOT NULL,
    dict_value VARCHAR(64) NOT NULL,
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    KEY idx_dict_type(dict_type)
);

CREATE TABLE IF NOT EXISTS sys_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL,
    content TEXT,
    cover_url VARCHAR(255),
    publish_status TINYINT DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content TEXT,
    read_status TINYINT DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    KEY idx_message_user(user_id)
);

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    ip_address VARCHAR(64),
    module_name VARCHAR(64),
    operation_type VARCHAR(64),
    request_uri VARCHAR(255),
    request_method VARCHAR(20),
    operation_content VARCHAR(255),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    KEY idx_operator_time(operator_id, created_time)
);

CREATE TABLE IF NOT EXISTS biz_book (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_name VARCHAR(64) NOT NULL,
    currency_code VARCHAR(16) NOT NULL DEFAULT 'CNY',
    is_default TINYINT DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    KEY idx_book_user(user_id)
);

CREATE TABLE IF NOT EXISTS biz_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    account_name VARCHAR(64) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    balance DECIMAL(12, 2) DEFAULT 0,
    color_tag VARCHAR(32),
    sort_order INT DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    KEY idx_account_book(book_id)
);

CREATE TABLE IF NOT EXISTS biz_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0,
    category_name VARCHAR(64) NOT NULL,
    category_type VARCHAR(16) NOT NULL,
    icon VARCHAR(64),
    level INT DEFAULT 1,
    sort_order INT DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    KEY idx_category_tree(book_id, parent_id, sort_order)
);

CREATE TABLE IF NOT EXISTS biz_bill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    bill_type VARCHAR(16) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    bill_date DATE NOT NULL,
    bill_time DATETIME,
    merchant_name VARCHAR(128),
    remark VARCHAR(255),
    source_type VARCHAR(32),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    KEY idx_bill_user_date(user_id, bill_date),
    KEY idx_bill_book_category(book_id, category_id)
);

CREATE TABLE IF NOT EXISTS biz_budget (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    budget_month VARCHAR(7) NOT NULL,
    budget_amount DECIMAL(12, 2) NOT NULL,
    used_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    KEY idx_budget_month(user_id, book_id, budget_month)
);

CREATE TABLE IF NOT EXISTS biz_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    bill_id BIGINT,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(255) NOT NULL,
    content_type VARCHAR(64),
    file_size BIGINT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    updated_by BIGINT DEFAULT 0,
    deleted TINYINT DEFAULT 0
);
