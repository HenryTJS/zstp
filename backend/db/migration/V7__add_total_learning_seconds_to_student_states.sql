-- 学生学习总时长（秒）；用于个人中心统计
ALTER TABLE student_states
ADD COLUMN IF NOT EXISTS total_learning_seconds BIGINT NOT NULL DEFAULT 0;
