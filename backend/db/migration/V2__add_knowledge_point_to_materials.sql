-- 为 materials 表添加 knowledge_point 字段（varchar 120，可为空）
ALTER TABLE materials ADD COLUMN IF NOT EXISTS knowledge_point VARCHAR(120);