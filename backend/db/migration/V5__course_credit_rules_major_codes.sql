-- 学分规则：由「一级/二级/三级分列」改为「一组学分 + major_codes JSON 数组」
-- 若表仍为旧结构，则迁移数据并删除旧列；否则跳过（由 Hibernate 维护新表时可无旧列）。

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'course_credit_rules' AND column_name = 'level1_code'
  ) THEN
    ALTER TABLE course_credit_rules ADD COLUMN IF NOT EXISTS major_codes jsonb DEFAULT '[]'::jsonb;

    UPDATE course_credit_rules r
    SET major_codes = (
      CASE
        WHEN r.level3_code IS NOT NULL AND btrim(r.level3_code) <> '' THEN to_jsonb(ARRAY[btrim(r.level3_code)])
        WHEN r.level2_code IS NOT NULL AND btrim(r.level2_code) <> '' THEN to_jsonb(ARRAY[btrim(r.level2_code)])
        WHEN r.level1_code IS NOT NULL AND btrim(r.level1_code) <> '' THEN to_jsonb(ARRAY[btrim(r.level1_code)])
        ELSE '[]'::jsonb
      END
    );

    ALTER TABLE course_credit_rules DROP CONSTRAINT IF EXISTS uk_course_credit_rule_course_major;
    ALTER TABLE course_credit_rules DROP COLUMN IF EXISTS level1_code;
    ALTER TABLE course_credit_rules DROP COLUMN IF EXISTS level2_code;
    ALTER TABLE course_credit_rules DROP COLUMN IF EXISTS level3_code;

    ALTER TABLE course_credit_rules ALTER COLUMN major_codes SET NOT NULL;
  END IF;
END $$;
