-- 学生已加入课程列表（JSON 字符串数组），与服务端学习状态一并持久化
ALTER TABLE student_states ADD COLUMN IF NOT EXISTS joined_courses_json TEXT DEFAULT '[]';
UPDATE student_states SET joined_courses_json = '[]' WHERE joined_courses_json IS NULL;
