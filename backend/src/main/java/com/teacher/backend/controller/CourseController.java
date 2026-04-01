package com.teacher.backend.controller;

import com.teacher.backend.service.CourseCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseCatalogService courseCatalogService;

    public CourseController(CourseCatalogService courseCatalogService) {
        this.courseCatalogService = courseCatalogService;
    }

    @GetMapping("/courses")
    public List<String> listCourses(@RequestParam(required = false) String majorCode) {
        // 目前课程由教师端维护，和专业无严格映射；返回全部课程供前端选择
        return courseCatalogService.allCourses();
    }
}
