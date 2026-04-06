package com.teacher.backend.service;

import java.util.List;

import com.teacher.backend.entity.TeacherCoursePermission;
import com.teacher.backend.entity.UserNotification;
import com.teacher.backend.repository.StudentStateRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.repository.UserNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscussionNotificationService {

    private final UserNotificationRepository notificationRepository;
    private final TeacherCoursePermissionRepository teacherCoursePermissionRepository;
    private final StudentStateRepository studentStateRepository;
    private final CourseCatalogService courseCatalogService;

    public DiscussionNotificationService(
            UserNotificationRepository notificationRepository,
            TeacherCoursePermissionRepository teacherCoursePermissionRepository,
            StudentStateRepository studentStateRepository,
            CourseCatalogService courseCatalogService) {
        this.notificationRepository = notificationRepository;
        this.teacherCoursePermissionRepository = teacherCoursePermissionRepository;
        this.studentStateRepository = studentStateRepository;
        this.courseCatalogService = courseCatalogService;
    }

    @Transactional
    public void notifyTeachersNewQa(long studentUserId, String courseName, String pointName, long postId, String studentUsername) {
        List<TeacherCoursePermission> perms = teacherCoursePermissionRepository.findByCourseNameOrderByTeacherIdAsc(courseName);
        for (TeacherCoursePermission p : perms) {
            Long tid = p.getTeacherId();
            if (tid == null || tid.equals(studentUserId)) {
                continue;
            }
            save(
                    tid,
                    "QA_POST",
                    "答疑帖",
                    studentUsername + " 在「" + pointName + "」提交了答疑",
                    courseName,
                    pointName,
                    postId);
        }
    }

    @Transactional
    public void notifyStudentsNewDiscussion(long teacherUserId, String courseName, String pointName, long postId, String teacherUsername) {
        String norm = courseCatalogService.normalizeCourseName(courseName);
        List<Long> userIds = studentStateRepository.findUserIdsWithCourseInJoined(norm);
        for (Long uid : userIds) {
            if (uid == null || uid.equals(teacherUserId)) {
                continue;
            }
            save(
                    uid,
                    "DISCUSSION_POST",
                    "讨论帖",
                    teacherUsername + " 在「" + pointName + "」发起了讨论",
                    courseName,
                    pointName,
                    postId);
        }
    }

    @Transactional
    public void notifyReply(Long parentAuthorId, long replierUserId, String replierName, String courseName, String pointName, long replyPostId) {
        if (parentAuthorId == null || parentAuthorId.equals(replierUserId)) {
            return;
        }
        save(parentAuthorId, "REPLY", "收到回复", replierName + " 回复了你", courseName, pointName, replyPostId);
    }

    @Transactional
    public void notifyLike(Long postAuthorId, long likerUserId, String likerName, String courseName, String pointName, long likedPostId) {
        if (postAuthorId == null || postAuthorId.equals(likerUserId)) {
            return;
        }
        save(postAuthorId, "LIKE", "收到点赞", likerName + " 赞了你", courseName, pointName, likedPostId);
    }

    private void save(Long userId, String type, String title, String body, String courseName, String pointName, Long postId) {
        UserNotification n = new UserNotification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setRead(false);
        n.setCourseName(courseName);
        n.setPointName(pointName);
        n.setPostId(postId);
        notificationRepository.save(n);
    }
}
