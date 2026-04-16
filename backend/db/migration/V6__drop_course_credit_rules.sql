-- 学分规则已下线：课程在雷达图计算中按课程等权处理
-- 清理遗留表，避免继续写入/维护无效数据结构
DROP TABLE IF EXISTS course_credit_rules;
