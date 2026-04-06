package com.teacher.backend.repository;

import java.util.List;
import java.util.Optional;

import com.teacher.backend.entity.StudentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentStateRepository extends JpaRepository<StudentState, Long> {

    @Query("SELECT s FROM StudentState s WHERE s.user.id = :userId")
    Optional<StudentState> findByUserId(@Param("userId") Long userId);

    /** 已加入某课程（joined_courses_json 含该课程名，精确匹配）的学生 user_id */
    @Query(
            value =
                    "SELECT s.user_id FROM student_states s "
                            + "WHERE EXISTS ("
                            + "  SELECT 1 FROM jsonb_array_elements_text("
                            + "    CASE WHEN s.joined_courses_json IS NULL OR btrim(s.joined_courses_json) = '' "
                            + "    THEN '[]'::jsonb ELSE s.joined_courses_json::jsonb END"
                            + "  ) AS elem(val) WHERE val = :courseName"
                            + ")",
            nativeQuery = true)
    List<Long> findUserIdsWithCourseInJoined(@Param("courseName") String courseName);
}
