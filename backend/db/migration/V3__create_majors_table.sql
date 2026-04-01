-- 专业三级联动表 majors
CREATE TABLE majors (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(16) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    parent_code VARCHAR(16),
    level INT NOT NULL,
    INDEX idx_parent_code(parent_code)
);
