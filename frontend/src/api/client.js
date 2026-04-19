// 根据专业code获取课程列表
export const listCoursesByMajor = (majorCode) => http.get('/courses', { params: { majorCode } })
export const listCourseCatalog = (userId) => http.get('/courses/catalog', { params: { userId } })
export const getCourseDetail = (courseName, userId) =>
  http.get('/courses/detail', { params: { courseName, ...(userId ? { userId } : {}) } })
export const updateCourseMeta = (payload) => http.put('/courses/meta', payload)
export const uploadCourseCover = (formData) => http.post('/courses/cover/upload', formData)

// 教师可见课程权限（申请经管理员审批后写入）
export const listTeacherCoursePermissions = (teacherId) =>
  http.get('/teacher-course-permissions', { params: { teacherId } })
export const listTeachersForCourses = (courseNames) =>
  http.post('/teacher-course-permissions/teachers-for-courses', { courseNames })

// 教师端申请课程权限（管理员端审批后可授予教师课程权限）
export const listTeacherCoursePermissionRequests = (payload) =>
  http.get('/teacher-course-permission-requests', { params: payload })
export const createTeacherCoursePermissionRequest = (payload) =>
  http.post('/teacher-course-permission-requests', payload)
export const decideTeacherCoursePermissionRequest = (payload) =>
  http.post('/teacher-course-permission-requests/decide', payload)

// 课程配置：五维权重（按课程名读写）
export const getCourseConfig = (courseName, params) =>
  http.get(`/course-configs/${encodeURIComponent(courseName)}`, { params })
export const updateCourseConfig = (courseName, payload) =>
  http.put(`/course-configs/${encodeURIComponent(courseName)}`, { courseName, ...payload })

// 学生维度分（用于雷达图）
export const fetchStudentDimensionScores = (userId, course) =>
  http.get('/student-dimension-scores', { params: { userId, ...(course ? { course } : {}) } })
import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  // 客观题/测试生成会多次请求 AI，部分场景下生成耗时更长
  // 这里把超时放大，避免 40s 内未返回导致整体失败
  timeout: 120000
})

export { http }

export const fetchMaterialsByKnowledgePoint = (courseName, knowledgePoint, includeAncestors = true, teacherId) =>
  http.get('/materials/by-knowledge-point', { params: { courseName, knowledgePoint, includeAncestors, ...(teacherId ? { teacherId } : {}) } })
export const fetchResourcesByKnowledgePoint = (params) =>
  http.get('/resources/by-knowledge-point', { params })
export const markResourceComplete = (payload) =>
  http.post('/resources/complete', payload)
export const fetchResourceProgress = (userId, courseName) =>
  http.get('/resources/progress', { params: { userId, courseName } })
export const fetchKnowledgeGraph = (payload) => http.post('/knowledge-graph', payload)
export const fetchLearningSuggestions = (payload) => http.post('/learning-suggestions', payload)
export const fetchMajorRelevance = (payload) => http.post('/major-relevance', payload)
export const askAiAgent = (payload) => http.post('/agent-chat', payload)
export const listKnowledgePoints = (courseName, teacherId) =>
  http.get('/knowledge-points', { params: { courseName, ...(teacherId ? { teacherId } : {}) } })
export const saveKnowledgePoint = (payload) => http.post('/knowledge-points', payload)
export const fetchQuestion = (payload) => http.post('/generate-question', payload)
export const fetchQuestions = (payload) => http.post('/generate-questions', payload)
export const fetchGrading = (payload) => http.post('/grade-answer', payload)
export const fetchExams = () => http.get('/exams')
export const deleteExam = (id) => http.delete('/exams/' + id)
export const saveExam = (payload) => http.post('/exams/save', payload)
export const loginUser = (payload) => http.post('/users/login', payload)
export const fetchAnnouncements = () => http.get('/announcements')
export const createAnnouncement = (payload) => http.post('/announcements', payload)
export const deleteAnnouncement = (id, userId) => http.delete(`/announcements/${id}`, { params: { userId } })
export const changePassword = (payload) => http.post('/users/change-password', payload)
export const listUsers = (role) => http.get('/users', { params: role ? { role } : {} })
export const updateUser = (payload) => http.post('/users/update', payload)
export const bulkImportUsers = (payload) => http.post('/users/bulk-import', payload)
export const uploadMaterial = (formData) =>
  // Let axios set Content-Type (with boundary) automatically for multipart requests
  http.post('/materials/upload', formData)
export const listMaterials = () => http.get('/materials')
export const fetchStudentState = (userId) => http.get('/student-state', { params: { userId } })
export const saveStudentState = (payload) => http.post('/student-state', payload)

/** 知识点交流区：发帖、列表、点赞（courseName + pointName 定位知识点） */
export const listKnowledgePointDiscussions = (params) =>
  http.get('/knowledge-point-discussions', { params })
export const countKnowledgePointDiscussionsByUser = (userId) =>
  http.get('/knowledge-point-discussions/count-by-user', { params: { userId } })
export const createKnowledgePointDiscussionPost = (payload) =>
  http.post('/knowledge-point-discussions', payload)
export const toggleKnowledgePointDiscussionLike = (postId, payload) =>
  http.post(`/knowledge-point-discussions/${postId}/like`, payload)
export const deleteKnowledgePointDiscussionPost = (postId, userId) =>
  http.delete(`/knowledge-point-discussions/${postId}`, { params: { userId } })

export const listUserNotifications = (userId, params) =>
  http.get('/notifications', { params: { userId, ...params } })
export const markUserNotificationRead = (id, userId) =>
  http.post(`/notifications/${id}/read`, null, { params: { userId } })
export const markAllUserNotificationsRead = (userId) =>
  http.post('/notifications/read-all', null, { params: { userId } })
export const deleteUserNotification = (id, userId) =>
  http.delete(`/notifications/${id}`, { params: { userId } })

/** 教师发布知识点测试（单选+填空）、学生拉取与提交 */
export const saveKnowledgePointPublishedTest = (payload) =>
  http.post('/knowledge-point-published-tests', payload)
export const getKnowledgePointPublishedTestForTeacher = (params) =>
  http.get('/knowledge-point-published-tests/for-teacher', { params })
export const getKnowledgePointPublishedTestForStudent = (params) =>
  http.get('/knowledge-point-published-tests/for-student', { params })
export const submitKnowledgePointPublishedTest = (payload) =>
  http.post('/knowledge-point-published-tests/submit', payload)
export const getMyKnowledgePointPublishedTestSubmission = (params) =>
  http.get('/knowledge-point-published-tests/my-submission', { params })
export const countPublishedTestsByTeacherCourses = (teacherUserId) =>
  http.get('/knowledge-point-published-tests/count-by-teacher-courses', { params: { teacherUserId } })

/** 教师端：某次知识点测试的学生逐题作答明细 */
export const getKnowledgePointPublishedTestSubmissionsDetail = (params) =>
  http.get('/knowledge-point-published-tests/submissions-detail', { params })

/** 教师端：学情分析报告（Markdown + 结构化数据，含 AI 教学建议） */
export const getKnowledgePointPublishedTestLearningReport = (params) =>
  http.get('/knowledge-point-published-tests/learning-report', { params })
