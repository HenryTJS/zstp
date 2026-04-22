UPDATE course_catalog_entry
SET cover_url = NULL
WHERE cover_url IS NOT NULL;
