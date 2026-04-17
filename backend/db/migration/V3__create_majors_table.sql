-- 专业三级联动表 majors
CREATE TABLE IF NOT EXISTS majors (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(16) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    parent_code VARCHAR(16),
    level INT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_majors_parent_code ON majors(parent_code);
