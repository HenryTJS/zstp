// 根据专业code获取课程列表
export const listCoursesByMajor = (majorCode) => http.get('/courses', { params: { majorCode } })
import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  // 客观题/测试生成会多次请求 AI，部分场景下生成耗时更长
  // 这里把超时放大，避免 40s 内未返回导致整体失败
  timeout: 120000
})

export { http }

export const fetchMaterialsByKnowledgePoint = (courseName, knowledgePoint, includeAncestors = true) =>
  http.get('/materials/by-knowledge-point', { params: { courseName, knowledgePoint, includeAncestors } })
export const fetchKnowledgeGraph = (payload) => http.post('/knowledge-graph', payload)
export const fetchLearningSuggestions = (payload) => http.post('/learning-suggestions', payload)
export const fetchMajorRelevance = (payload) => http.post('/major-relevance', payload)
export const listKnowledgePoints = (courseName) => http.get('/knowledge-points', { params: { courseName } })
export const saveKnowledgePoint = (payload) => http.post('/knowledge-points', payload)
export const fetchQuestion = (payload) => http.post('/generate-question', payload)
export const fetchQuestions = (payload) => http.post('/generate-questions', payload)
export const fetchGrading = (payload) => http.post('/grade-answer', payload)
export const fetchTest = (payload) => http.post('/generate-test', payload)
export const fetchExam = (payload) => http.post('/generate-exam', payload)
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
