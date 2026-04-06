<script setup>
import StudentGraph from './StudentGraph.vue'
import StudentExercise from './StudentExercise.vue'
import StudentPaperComposer from './StudentPaperComposer.vue'
import StudentReview from './StudentReview.vue'
import StudentAnnouncements from './StudentAnnouncements.vue'
import StudentCourses from './StudentCourses.vue'
import StudentHome from './StudentHome.vue'
import StudentEditProfileModal from './StudentEditProfileModal.vue'
import StudentChangePasswordModal from './StudentChangePasswordModal.vue'
import KnowledgePointDiscussion from './KnowledgePointDiscussion.vue'
const props = defineProps({
  currentUser: {
    type: Object,
    required: true
  },
  activePage: {
    type: String,
    default: 'home'
  }
})
const emit = defineEmits(['navigate', 'logout', 'update-user'])

const route = useRoute()
const router = useRouter()
/** 通知深链：选中图谱中的知识点标签 */
const pendingGraphPointLabel = ref('')
/** 通知深链：交流区滚动锚点帖子 id */
const discussionFocusPostId = ref(null)

const profileForm = ref({ username: '', email: '', role: '' })
const practiceAnswer = ref('')
const generatedQuestion = ref(null)
const questionLoading = ref(false)
const questionError = ref('')
const practiceError = ref('')
const practiceLoading = ref(false)
const profileMessage = ref('')
const stateHydrated = ref(false)
let stateSaveTimer = null
const answerImageFile = ref(null)
const answerImageBase64 = ref('')
const selectedChoiceAnswer = ref('')
const practiceResult = ref(null)
const currentPage = ref(props.activePage || 'home')

// 监听外部 activePage prop，驱动内部 currentPage 切换
watch(
  () => props.activePage,
  (val) => {
    if (val && val !== currentPage.value) {
      currentPage.value = val
    }
  }
)
const learningRecords = ref([])
const wrongBook = ref([])
const activeQuestionType = computed(() => generatedQuestion.value?.question_type || questionForm.value.questionType)
const userInitial = computed(() => props.currentUser?.username?.charAt(0) || '')
const selectedNode = computed(() => {
  return graphData.value.nodes.find(n => n.id === selectedNodeId.value) || null
})
const relevanceLabel = computed(() => {
  const level = Number(majorRelevance.value?.scoreLevel || 0)
  if (level >= 5) return '高度相关'
  if (level >= 4) return '较强相关'
  if (level >= 3) return '中等相关'
  if (level >= 2) return '弱相关'
  if (level >= 1) return '很弱相关'
  return ''
})
import * as echarts from 'echarts'
import katex from 'katex'
import { Teleport, computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AiAssistantWidget from './AiAssistantWidget.vue'
import {
  fetchGrading,
  fetchKnowledgeGraph,
  fetchQuestion,
  fetchQuestions,
  fetchStudentState,
  saveStudentState,
  fetchMaterialsByKnowledgePoint,
  updateUser,
  listKnowledgePoints,
  fetchExams,
  deleteExam,
  fetchLearningSuggestions,
  fetchMajorRelevance,
  saveExam,
  fetchAnnouncements,
  listCoursesByMajor,
  listTeachersForCourses,
  getKnowledgePointPublishedTestForStudent,
  submitKnowledgePointPublishedTest
} from '../api/client'
const materials = ref([])
const selectedKnowledgePoint = ref('')

import { http } from '../api/client'
const joinedCourses = ref([])
const joinedCoursesSearch = ref('')
const joinedCoursesPage = ref(1)
const coursePageSize = 8

/** 课程名 -> 拥有该课程查看权限的教师 [{ teacherId, username }]（在 availableCourses 定义后填充） */
const teachersByCourse = ref({})
const teachersByCourseLoading = ref(false)

/** 旧版仅存在浏览器本地的已加入课程，首次登录时合并进服务端并删除该键 */
const readLegacyJoinedCoursesFromLocalStorage = () => {
  const id = props.currentUser?.id
  if (!id) return []
  try {
    const raw = localStorage.getItem(`student-joined-courses:${id}`)
    const arr = JSON.parse(raw || '[]')
    const list = Array.isArray(arr) ? arr.filter((x) => typeof x === 'string' && x.trim()) : []
    if (list.length) {
      localStorage.removeItem(`student-joined-courses:${id}`)
    }
    return list
  } catch {
    return []
  }
}
// 专业三级联动数据
const majorLevel1 = ref([])
const majorLevel2 = ref([])
const majorLevel3 = ref([])
const selectedMajor1 = ref('')
const selectedMajor2 = ref('')
const selectedMajor3 = ref('')

const loadMajorLevel1 = async () => {
  try {
    const { data } = await http.get('/majors/tree?level=1')
    majorLevel1.value = Array.isArray(data) ? data : []
  } catch (err) {
    console.error('loadMajorLevel1 failed', err)
    majorLevel1.value = []
  }
}
const loadMajorLevel2 = async (parentCode) => {
  if (!parentCode) { majorLevel2.value = []; return }
  try {
    const { data } = await http.get('/majors/tree?level=2&parentCode=' + parentCode)
    majorLevel2.value = Array.isArray(data) ? data : []
  } catch (err) {
    console.error('loadMajorLevel2 failed', err)
    majorLevel2.value = []
  }
}
const loadMajorLevel3 = async (parentCode) => {
  if (!parentCode) { majorLevel3.value = []; return }
  try {
    const { data } = await http.get('/majors/tree?level=3&parentCode=' + parentCode)
    majorLevel3.value = Array.isArray(data) ? data : []
  } catch (err) {
    console.error('loadMajorLevel3 failed', err)
    majorLevel3.value = []
  }
}

const onEditMajor1Change = async () => {
  const code = editProfileForm.value.major1
  editProfileForm.value.major2 = ''
  editProfileForm.value.major3 = ''
  if (!code) {
    majorLevel2.value = []
    majorLevel3.value = []
    return
  }
  await loadMajorLevel2(code)
}

const onEditMajor2Change = async () => {
  const code = editProfileForm.value.major2
  editProfileForm.value.major3 = ''
  if (!code) {
    majorLevel3.value = []
    return
  }
  await loadMajorLevel3(code)
}

watch(selectedMajor1, (val) => {
  selectedMajor2.value = ''
  selectedMajor3.value = ''
  loadMajorLevel2(val)
  majorLevel3.value = []
})
watch(selectedMajor2, (val) => {
  selectedMajor3.value = ''
  loadMajorLevel3(val)
})

onMounted(() => {
  loadMajorLevel1()
})
// 课程列表由教师端维护，选中专业后动态请求
const availableCourses = ref([])

let majorCoursesReqId = 0
/** 从服务器恢复学生状态期间：不因 availableCourses 尚未就绪而裁剪 joinedCourses */
let hydrateJoiningCourses = false

const fetchAvailableCoursesForCurrentMajor = async () => {
  const code = selectedMajor3.value || selectedMajor2.value || selectedMajor1.value
  const reqId = ++majorCoursesReqId
  if (!code) {
    availableCourses.value = []
    return reqId
  }
  try {
    const { data } = await listCoursesByMajor(code)
    if (reqId !== majorCoursesReqId) return reqId
    availableCourses.value = Array.isArray(data) ? data : []
  } catch {
    if (reqId !== majorCoursesReqId) return reqId
    availableCourses.value = []
  }
  return reqId
}

const loadTeachersForMarketCourses = async () => {
  if (!availableCourses.value.length) {
    teachersByCourse.value = {}
    return
  }
  teachersByCourseLoading.value = true
  try {
    const { data } = await listTeachersForCourses(availableCourses.value)
    teachersByCourse.value = data && typeof data === 'object' ? data : {}
  } catch {
    teachersByCourse.value = {}
  } finally {
    teachersByCourseLoading.value = false
  }
}

const formatTeachersForCourse = (courseName) => {
  const list = teachersByCourse.value[courseName]
  if (!Array.isArray(list) || !list.length) return ''
  return list.map((t) => t?.username || '').filter(Boolean).join('、')
}
watch([selectedMajor1, selectedMajor2, selectedMajor3], async () => {
  const appliedReqId = await fetchAvailableCoursesForCurrentMajor()
  if (appliedReqId !== majorCoursesReqId) return
  if (hydrateJoiningCourses) return
  // 课程列表尚未加载出结果时不裁剪 joinedCourses，避免误清空服务端已持久化的加入列表
  if (!availableCourses.value.length) {
    ensureCourseSelection()
    return
  }
  // 专业切换后仅保留当前专业课程广场里仍存在的已加入课程
  joinedCourses.value = joinedCourses.value.filter((c) => availableCourses.value.includes(c))
  ensureCourseSelection()
  schedulePersistStudentState()
}, { immediate: true })

const selectedMajor = computed({
  get() {
    return selectedMajor3.value || selectedMajor2.value || selectedMajor1.value || ''
  },
  set(val) {
    // 仅用于回显，实际保存 code
    // 这里可根据 code 反查 name 并设置级联
  }
})

const selectedMajorDisplay = computed(() => {
  const code = selectedMajor3.value || selectedMajor2.value || selectedMajor1.value || ''
  if (!code) return ''
  const findName = (list, target) => {
    for (const m of list || []) {
      if (m.code === target) return m.name
      if (Array.isArray(m.subfields)) {
        const r = findName(m.subfields, target)
        if (r) return r
      }
    }
    return null
  }
  return findName(majorLevel1.value, code) || code
})
/** 个人中心「统计课程」下拉，仅影响首页统计筛选，不单独决定知识图谱/做题上下文 */
const selectedCourse = ref('')
/** 从课程广场「进入课程」选择的当前学习课程（须已加入） */
const learningContextCourse = ref('')
/** 未加入时「查看课程」仅浏览图谱，不可进行资料/建议等交互 */
const previewUnjoinedCourse = ref('')

const isUnjoinedPreviewMode = computed(() => Boolean(previewUnjoinedCourse.value))
/** 出题/做题/错题学习页：须已从广场进入且已加入该课 */
const canStudyCurrentCourse = computed(() => {
  const c = learningContextCourse.value
  return Boolean(c && joinedCourses.value.includes(c))
})
/** 知识图谱页：已进入的学习课程，或未加入的预览 */
const canShowGraphPage = computed(() => {
  if (previewUnjoinedCourse.value) return true
  return canStudyCurrentCourse.value
})
const filteredMarketCourses = computed(() => {
  const kw = (joinedCoursesSearch.value || '').trim().toLowerCase()
  if (!kw) return availableCourses.value
  return availableCourses.value.filter((c) => String(c || '').toLowerCase().includes(kw))
})
const marketTotalPages = computed(() => Math.max(1, Math.ceil(filteredMarketCourses.value.length / coursePageSize)))
const pagedMarketCourses = computed(() => {
  const page = Math.min(Math.max(1, joinedCoursesPage.value), marketTotalPages.value)
  const start = (page - 1) * coursePageSize
  return filteredMarketCourses.value.slice(start, start + coursePageSize)
})

const graphLoading = ref(false)
const graphError = ref('')
const graphData = ref({ title: '', nodes: [], edges: [], suggestions: [] })
const selectedNodeId = ref('')
// 记录点击节点的深度类别：0=课程名, 1=章节名, 2=节, 3=更深层扩展
const graphClickedNodeCategory = ref(null)
const graphChartRef = ref(null)
let graphChart = null

// 测试：从课程名/章节名/节开始（不在第 3 级叶子上直接测试）
const practiceTestAllowed = computed(() => graphClickedNodeCategory.value !== 3)

// 学习建议：不再只依赖 knowledge-graph 返回的固定建议，而是对“当前知识点”实时调用 API 生成
const learningSuggestions = ref([])
const suggestionLoading = ref(false)
const suggestionError = ref('')
let suggestionReqId = 0
const majorRelevance = ref({
  scoreLevel: null,
  summary: '',
  relatedContents: [],
  lowRelevanceReason: ''
})
const relevanceLoading = ref(false)
const relevanceError = ref('')
let relevanceReqId = 0


const questionForm = ref({
  knowledgePoint: '',
  difficulty: '中等',
  questionType: '解答题'
})

const examError = ref('')

const savedExams = ref([])

const loadSavedExams = async () => {
  try {
    const { data } = await fetchExams()
    savedExams.value = Array.isArray(data) ? data : []
  } catch (e) {
    savedExams.value = []
  }
}

// 加载当前课程的知识点（含 parentPoint，用于三级联选）
const examPointsRaw = ref([])
const loadExamPoints = async () => {
  try {
    const { data } = await listKnowledgePoints(learningContextCourse.value)
    const points = Array.isArray(data?.points)
      ? data.points
      : Array.isArray(data)
        ? data
        : []
    examPointsRaw.value = points
  } catch (e) {
    examPointsRaw.value = []
  }
}

const kpCascade1 = ref('')
const kpCascade2 = ref('')
const kpCascade3 = ref('')

const sortPoints = (a, b) => {
  const oa = Number(a?.sortOrder ?? 0)
  const ob = Number(b?.sortOrder ?? 0)
  if (oa !== ob) return oa - ob
  return String(a?.pointName || '').localeCompare(String(b?.pointName || ''), 'zh-CN')
}

/** 一级：父节点为课程名，或旧数据无父节点且名称不等于课程名（课程名单独作为 0 级） */
const kpLevel1Options = computed(() => {
  const cn = learningContextCourse.value
  return examPointsRaw.value
    .filter((p) => {
      if (!cn || p?.pointName === cn) return false
      const parent = p?.parentPoint && String(p.parentPoint).trim()
      if (!parent) return true
      return parent === cn
    })
    .slice()
    .sort(sortPoints)
})

/** 二级：当前一级的子节点 */
const kpLevel2Options = computed(() => {
  const parent = kpCascade1.value
  if (!parent) return []
  return examPointsRaw.value.filter((p) => p?.parentPoint === parent).slice().sort(sortPoints)
})

/** 三级：当前二级的子节点 */
const kpLevel3Options = computed(() => {
  const parent = kpCascade2.value
  if (!parent) return []
  return examPointsRaw.value.filter((p) => p?.parentPoint === parent).slice().sort(sortPoints)
})

watch(kpCascade1, () => {
  kpCascade2.value = ''
  kpCascade3.value = ''
})
watch(kpCascade2, () => {
  kpCascade3.value = ''
})

/** 最深层联选；若未选一二三级则使用当前课程名（0 级） */
const pickCascadePoint = () =>
  kpCascade3.value || kpCascade2.value || kpCascade1.value || learningContextCourse.value || ''

const clearCascadeAfterAdd = () => {
  if (kpCascade3.value) {
    kpCascade3.value = ''
    return
  }
  if (kpCascade2.value) {
    kpCascade2.value = ''
    return
  }
  if (kpCascade1.value) {
    kpCascade1.value = ''
  }
  // 仅添加 0 级（课程名）时一～三级本就为空，无需额外处理
}

// 从知识图谱点击节点后：进入“固定规则”的测试
const enterFixedTestFromGraph = () => {
  if (!selectedKnowledgePoint.value) return
  if (!practiceTestAllowed.value) return
  // 固定：5题、仅单选
  testCounts.value.singleChoiceCount = 5
  testCounts.value.multiChoiceCount = 0
  testCounts.value.judgeCount = 0
  testCounts.value.fillCount = 0

  if (!Array.isArray(testForm.value.selectedPoints)) testForm.value.selectedPoints = []
  testForm.value.selectedPoints = [String(selectedKnowledgePoint.value).trim()]

  currentPage.value = 'exercise'
}

// =========================
// 测试模式（客观题多题）
// =========================

// 从题型数量生成测试（一次最多 10 题）
const testCounts = ref({
  singleChoiceCount: 0,
  multiChoiceCount: 0,
  judgeCount: 0,
  fillCount: 0
})

// 测试知识点：支持多选（用于生成题目 topic）
const testForm = ref({
  selectedPoints: []
})

const addTestPoint = () => {
  const p = pickCascadePoint()
  if (!p) return
  if (!Array.isArray(testForm.value.selectedPoints)) testForm.value.selectedPoints = []
  if (!testForm.value.selectedPoints.includes(p)) {
    testForm.value.selectedPoints.push(p)
  }
  clearCascadeAfterAdd()
}

const removeTestPoint = (p) => {
  if (!Array.isArray(testForm.value.selectedPoints)) return
  testForm.value.selectedPoints = testForm.value.selectedPoints.filter((x) => x !== p)
}

const testQuestions = ref([]) // 后端生成的题目数组
const testAnswers = ref([]) // 每题的用户作答（单选/判断: string，多选: string[]，填空: string）
const testLoading = ref(false)
const testError = ref('')
const testSubmitted = ref(false)
const testResult = ref(null) // { totalScore, fullScore, perQuestionScores: [...] }

/** 教师发布知识点测试（练习页） */
const teacherKpTest = ref(null)
const teacherKpTestLoading = ref(false)
const teacherKpTestError = ref('')
const teacherKpTestAnswers = ref([])
const teacherKpTestSubmitted = ref(false)
const teacherKpTestResult = ref(null)
const teacherKpTestSubmitting = ref(false)

const loadTeacherKpTest = async () => {
  teacherKpTest.value = null
  teacherKpTestError.value = ''
  teacherKpTestSubmitted.value = false
  teacherKpTestResult.value = null
  teacherKpTestAnswers.value = []
  if (!canStudyCurrentCourse.value || !learningContextCourse.value || !selectedKnowledgePoint.value || !props.currentUser?.id) {
    return
  }
  teacherKpTestLoading.value = true
  try {
    const { data } = await getKnowledgePointPublishedTestForStudent({
      courseName: learningContextCourse.value,
      pointName: selectedKnowledgePoint.value,
      userId: props.currentUser.id
    })
    const t = data?.test
    if (t && Array.isArray(t.questions) && t.questions.length) {
      teacherKpTest.value = t
      teacherKpTestAnswers.value = t.questions.map(() => '')
    } else {
      teacherKpTest.value = null
    }
  } catch (e) {
    teacherKpTestError.value = e?.response?.data?.message || ''
    teacherKpTest.value = null
  } finally {
    teacherKpTestLoading.value = false
  }
}

watch(
  () => [currentPage.value, learningContextCourse.value, selectedKnowledgePoint.value, props.currentUser?.id],
  () => {
    if (currentPage.value !== 'exercise') {
      teacherKpTest.value = null
      teacherKpTestSubmitted.value = false
      teacherKpTestResult.value = null
      teacherKpTestAnswers.value = []
      teacherKpTestError.value = ''
      return
    }
    void loadTeacherKpTest()
  }
)

const submitTeacherKpTest = async () => {
  teacherKpTestError.value = ''
  if (!teacherKpTest.value?.id || !props.currentUser?.id) return
  const qs = teacherKpTest.value.questions || []
  if (!Array.isArray(teacherKpTestAnswers.value) || teacherKpTestAnswers.value.length !== qs.length) {
    teacherKpTestError.value = '作答数据异常。'
    return
  }
  for (let i = 0; i < qs.length; i++) {
    if (!String(teacherKpTestAnswers.value[i] ?? '').trim()) {
      teacherKpTestError.value = `请完成第 ${i + 1} 题。`
      return
    }
  }
  teacherKpTestSubmitting.value = true
  try {
    const { data } = await submitKnowledgePointPublishedTest({
      userId: props.currentUser.id,
      testId: teacherKpTest.value.id,
      answers: teacherKpTestAnswers.value.map((a) => String(a ?? '').trim())
    })
    teacherKpTestResult.value = data
    teacherKpTestSubmitted.value = true

    const now = new Date()
    const nowDisplay = now.toLocaleString()
    const nowIso = now.toISOString()
    const per = Array.isArray(data?.perQuestion) ? data.perQuestion : []
    const kpAnchor = String(selectedKnowledgePoint.value || '').trim()
    const newLR = []
    const newWB = []
    for (let i = 0; i < per.length; i++) {
      const row = per[i]
      const score = Number(row.score ?? 0)
      const full = Number(row.full_score ?? 0)
      const kpRow =
        String(qs[i]?.focusPointName || '').trim() ||
        String(row.focusPointName || '').trim() ||
        kpAnchor ||
        '未标注'
      newLR.push({
        id: `lr-tt-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 7)}`,
        time: nowDisplay,
        major: selectedMajorDisplay.value || selectedMajor.value || '',
        course: learningContextCourse.value || '',
        knowledgePoint: kpRow,
        score,
        fullScore: full
      })
      if (score < full) {
        newWB.push({
          id: `wb-tt-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 7)}`,
          major: selectedMajorDisplay.value || selectedMajor.value || '',
          course: learningContextCourse.value || '',
          knowledgePoint: kpRow,
          question: row.question || '',
          explanation: row.explanation || '',
          myAnswer: String(teacherKpTestAnswers.value[i] ?? ''),
          answer: '',
          score,
          fullScore: full,
          collectedAt: nowDisplay,
          collectedAtIso: nowIso
        })
      }
    }
    learningRecords.value = [...newLR, ...(learningRecords.value || [])]
    wrongBook.value = [...newWB, ...(wrongBook.value || [])]
    schedulePersistStudentState()
  } catch (e) {
    teacherKpTestError.value = e?.response?.data?.message || '提交失败'
  } finally {
    teacherKpTestSubmitting.value = false
  }
}

const testTotalCount = computed(() =>
  Number(testCounts.value.singleChoiceCount || 0) +
  Number(testCounts.value.multiChoiceCount || 0) +
  Number(testCounts.value.judgeCount || 0) +
  Number(testCounts.value.fillCount || 0)
)

const resetTestState = () => {
  testQuestions.value = []
  testAnswers.value = []
  testSubmitted.value = false
  testResult.value = null
  testError.value = ''
}

const parseOptionLetter = (opt) => {
  const s = String(opt || '').trim()
  const m = s.match(/^[A-D]\b/i)
  if (m) return m[0].toUpperCase()
  // 支持 "A." "A、" 等
  const m2 = s.match(/^[A-D][\.\、\)]/)
  if (m2) return s.charAt(0).toUpperCase()
  return ''
}

const parseOptionText = (opt) => {
  const s = String(opt || '').trim()
  // 去掉开头的 A./B./... 形式
  return s.replace(/^[A-D][\.\、\)]\s*/i, '')
}

const resolveAnswerText = (q, answer) => {
  const qt = q?.question_type
  if (!qt) return String(answer || '').trim()
  if (qt === '选择题' || qt === '判断题') {
    const letter = Array.isArray(answer) ? String(answer[0] || '').trim() : String(answer || '').trim()
    const L = letter ? letter.toUpperCase().charAt(0) : ''
    const opt = (q.options || []).find(o => parseOptionLetter(o) === L)
    return opt ? parseOptionText(opt) : L
  }
  if (qt === '多选题') {
    const list = Array.isArray(answer) ? answer : String(answer || '').split('')
    const letters = list.map(x => String(x).toUpperCase().trim()).filter(x => ['A', 'B', 'C', 'D'].includes(x))
    const ordered = ['A', 'B', 'C', 'D'].filter(l => letters.includes(l))
    const texts = (q.options || []).filter(o => ordered.includes(parseOptionLetter(o))).map(parseOptionText)
    return texts.join('、') || ''
  }
  // 填空题
  return String(answer || '').trim()
}

const gradeStudentAnswer = (q, a) => {
  const qt = q?.question_type
  if (!qt) return ''
  if (qt === '多选题') {
    if (!Array.isArray(a)) return ''
    return a.map(x => String(x).toUpperCase()).filter(x => ['A', 'B', 'C', 'D'].includes(x)).sort().join('')
  }
  return String(a || '').trim()
}

const unescapeNewlinesSafe = (t) => {
  // 保留原始转义字符（如 \n、\t），避免影响 Markdown 内容语义
  if (t === null || t === undefined) return t
  return String(t)
}

const generateTest = async () => {
  resetTestState()
  testError.value = ''
  testLoading.value = true
  try {
    const topics = Array.isArray(testForm.value.selectedPoints) ? testForm.value.selectedPoints : []
    if (!topics.length) {
      testError.value = '请选择一个或多个知识点。'
      return
    }
    const total = testTotalCount.value
    if (total < 1) {
      testError.value = '请至少选择一种题型数量。'
      return
    }
    if (total > 10) {
      testError.value = '一次最多 10 题，请减少题目数量。'
      return
    }

    // 生成顺序：单选 -> 多选 -> 判断 -> 填空（便于用户阅读）
    const typeList = []
    for (let i = 0; i < Number(testCounts.value.singleChoiceCount || 0); i++) typeList.push('选择题')
    for (let i = 0; i < Number(testCounts.value.multiChoiceCount || 0); i++) typeList.push('多选题')
    for (let i = 0; i < Number(testCounts.value.judgeCount || 0); i++) typeList.push('判断题')
    for (let i = 0; i < Number(testCounts.value.fillCount || 0); i++) typeList.push('填空题')

    const diff = questionForm.value.difficulty
    const major = selectedMajor.value

    // 多知识点轮换生成：第 i 题使用 topics[i % topics.length]
    // 并发策略（多线程/并发请求）：
    // - 总题数最多 10（UI 已校验）
    // - API 最多发起 5 次请求
    // - 每次请求返回 1 或 2 题（后端 /api/generate-questions）
    const specs = typeList.map((qt, idx) => {
      const kp = topics[idx % topics.length]
      let topicLabel = kp
      // 若该知识点在图谱中有下属知识点，则随机选一个一起作为提示，增加多样性
      const sub = pickRandomSubpointLabelForKnowledgePoint(kp)
      if (sub) {
        topicLabel = `${kp}：${sub}`
      }
      const topic = composeTopic(topicLabel) + `（题${idx + 1}）`
      return { topic, difficulty: diff, questionType: qt, major }
    })

    const chunk2 = (arr) => {
      const out = []
      for (let i = 0; i < arr.length; i += 2) out.push(arr.slice(i, i + 2))
      return out
    }

    const batches = chunk2(specs).slice(0, 5)
    if (batches.length > 5) {
      testError.value = '生成请求次数超限（最多 5 次），请减少题目数量。'
      return
    }

    const responses = await Promise.all(
      batches.map((items) => fetchQuestions({ items }))
    )

    const results = []
    for (const resp of responses) {
      const data = resp && resp.data ? resp.data : resp
      const qs = Array.isArray(data?.questions) ? data.questions : []
      for (const q of qs) results.push(q)
    }

    // 兜底：确保不超过 10 题（即便后端异常返回更多）
    const trimmed = results.slice(0, 10)

    testQuestions.value = (trimmed || [])
      .map((q, i) => {
        if (!q) return q
        const anchorKp = topics[i % topics.length]
        return {
          ...q,
          // 记录出题时轮换到的用户知识点，避免 AI 在 knowledge_points 里编造名称误导错题本
          userKnowledgePoint: anchorKp,
          question: unescapeNewlinesSafe(q.question),
          explanation: unescapeNewlinesSafe(q.explanation),
          answer: unescapeNewlinesSafe(q.answer),
          options: Array.isArray(q.options) ? q.options.map(unescapeNewlinesSafe) : [],
          knowledge_points: Array.isArray(q.knowledge_points) ? q.knowledge_points.map(unescapeNewlinesSafe) : []
        }
      })

    testAnswers.value = testQuestions.value.map((q) => {
      if (q.question_type === '多选题') return []
      return ''
    })
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || (typeof e === 'string' ? e : '测试生成失败，请稍后重试。')
    // 保留更完整的错误信息，便于你直接定位后端原因
    testError.value = msg
    console.error('[generateTest] failed:', e)
  } finally {
    testLoading.value = false
  }
}

const submitTest = async () => {
  testError.value = ''
  if (!testQuestions.value.length) {
    testError.value = '请先生成测试。'
    return
  }
  if (!Array.isArray(testAnswers.value) || testAnswers.value.length !== testQuestions.value.length) {
    testError.value = '作答数据异常，请刷新后重试。'
    return
  }

  // 校验作答完整性
  for (let i = 0; i < testQuestions.value.length; i++) {
    const q = testQuestions.value[i]
    const a = testAnswers.value[i]
    const qt = q?.question_type
    if (qt === '多选题') {
      if (!Array.isArray(a) || a.length === 0) {
        testError.value = `第 ${i + 1} 题：请至少选择一个选项。`
        return
      }
    } else {
      if (!String(a || '').trim()) {
        testError.value = `第 ${i + 1} 题：请填写/选择答案。`
        return
      }
    }
  }

  testLoading.value = true
  try {
    const now = new Date()
    const nowIso = now.toISOString()
    const nowDisplay = now.toLocaleString()
    const graders = testQuestions.value.map((q, idx) => {
      const studentAnswer = gradeStudentAnswer(q, testAnswers.value[idx])
      return fetchGrading({
        question: q.question,
        referenceAnswer: q.answer,
        studentAnswer,
        questionType: q.question_type,
        studentAnswerImageBase64: '',
        studentAnswerImageName: '',
        fullScore: 10
      })
    })

    const results = await Promise.all(graders)
    const perQuestionScores = results.map((r) => (r && r.data ? r.data : r)).map((d) => d || {})
    const totalScore = perQuestionScores.reduce((sum, r) => sum + Number(r.score || 0), 0)
    const fullScore = perQuestionScores.length * 10

    testResult.value = { totalScore, fullScore, perQuestionScores }
    testSubmitted.value = true

    // 将本次作答写入学习记录与错题本，供“错题与记录”页面展示
    const newLearningRecords = []
    const newWrongItems = []
    for (let i = 0; i < testQuestions.value.length; i++) {
      const q = testQuestions.value[i] || {}
      const result = perQuestionScores[i] || {}
      const score = Number(result.score || 0)
      const full = 10
      const kpUserAnchored = String(q.userKnowledgePoint || '').trim()
      const kpFromForm =
        Array.isArray(testForm.value.selectedPoints) && testForm.value.selectedPoints.length
          ? String(testForm.value.selectedPoints[i % testForm.value.selectedPoints.length] || '').trim()
          : ''
      const kpFromAi =
        Array.isArray(q.knowledge_points) && q.knowledge_points.length
          ? String(q.knowledge_points[0] || '').trim()
          : ''
      const kp = kpUserAnchored || kpFromForm || kpFromAi || ''

      newLearningRecords.push({
        id: `lr-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 7)}`,
        time: nowDisplay,
        major: selectedMajorDisplay.value || selectedMajor.value || '',
        course: learningContextCourse.value || '',
        knowledgePoint: kp || '未标注',
        score,
        fullScore: full
      })

      if (score < full) {
        newWrongItems.push({
          id: `wb-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 7)}`,
          major: selectedMajorDisplay.value || selectedMajor.value || '',
          course: learningContextCourse.value || '',
          knowledgePoint: kp || '未标注',
          question: q.question || '',
          explanation: unescapeNewlinesSafe(q.explanation) || '',
          myAnswer: resolveAnswerText(q, testAnswers.value[i]),
          answer: resolveAnswerText(q, q.answer),
          score,
          fullScore: full,
          collectedAt: nowDisplay,
          collectedAtIso: nowIso
        })
      }
    }

    learningRecords.value = [...newLearningRecords, ...(learningRecords.value || [])]
    wrongBook.value = [...newWrongItems, ...(wrongBook.value || [])]
    schedulePersistStudentState()
  } catch (e) {
    testError.value = e?.response?.data?.message || '提交失败，请稍后重试。'
  } finally {
    testLoading.value = false
  }
}

const filteredLearningRecords = computed(() => {
  if (!selectedCourse.value) {
    return learningRecords.value
  }
  return learningRecords.value.filter((item) => item.course === selectedCourse.value)
})

const filteredWrongBook = computed(() => {
  if (!selectedCourse.value) {
    return wrongBook.value
  }
  return wrongBook.value.filter((item) => item.course === selectedCourse.value)
})

/** 「错题与记录」页：按当前「进入课程」筛选，与个人中心统计课程无关 */
const filteredWrongBookForLearningPage = computed(() => {
  const c = learningContextCourse.value
  if (!c) return []
  return wrongBook.value.filter((item) => item.course === c)
})
const filteredLearningRecordsForLearningPage = computed(() => {
  const c = learningContextCourse.value
  if (!c) return []
  return learningRecords.value.filter((item) => item.course === c)
})

/** 知识图谱掌握度：按当前「进入课程」的学习上下文统计，与个人中心统计课程无关 */
const learningRecordsForGraph = computed(() => {
  const c = learningContextCourse.value
  if (!c) return []
  return learningRecords.value.filter((item) => item.course === c)
})

/**
 * 按知识图谱「包含」边（不含前置）从某节点向下遍历，得到该节点及所有下级节点对应的 label 集合，
 * 用于把练习记录汇总到祖先知识点。
 */
const collectDescendantLabelsFromGraph = (startId) => {
  const nodes = graphData.value.nodes || []
  const rawEdges = graphData.value.edges || []
  const byId = new Map(nodes.map((n) => [n.id, n]))
  const childMap = new Map()
  for (const edge of rawEdges) {
    if ((edge.label || '').toString().includes('前置')) continue
    if (!childMap.has(edge.source)) childMap.set(edge.source, [])
    childMap.get(edge.source).push(edge.target)
  }
  const visited = new Set()
  const stack = [startId]
  while (stack.length) {
    const id = stack.pop()
    if (!id || visited.has(id)) continue
    visited.add(id)
    for (const t of childMap.get(id) || []) {
      if (!visited.has(t)) stack.push(t)
    }
  }
  const labels = new Set()
  for (const id of visited) {
    const n = byId.get(id)
    const lab = n?.label && String(n.label).trim()
    if (lab) labels.add(lab)
  }
  return labels
}

// 为当前知识点选择一个随机下属知识点 label，用于丰富出题提示词（若无下属则返回 null）
const pickRandomSubpointLabelForKnowledgePoint = (label) => {
  const nodes = graphData.value.nodes || []
  const edges = graphData.value.edges || []
  if (!nodes.length) return null
  const trimmed = String(label || '').trim()
  if (!trimmed) return null
  const byId = new Map(nodes.map((n) => [n.id, n]))
  const byLabel = new Map()
  for (const n of nodes) {
    const lab = n?.label && String(n.label).trim()
    if (lab && !byLabel.has(lab)) {
      byLabel.set(lab, n.id)
    }
  }
  const startId = byLabel.get(trimmed)
  if (!startId) return null

  const childMap = new Map()
  for (const edge of edges) {
    if ((edge.label || '').toString().includes('前置')) continue
    if (!childMap.has(edge.source)) childMap.set(edge.source, [])
    childMap.get(edge.source).push(edge.target)
  }
  const children = childMap.get(startId) || []
  if (!children.length) return null
  const childNodes = children
    .map((id) => byId.get(id))
    .filter((n) => n && n.label && String(n.label).trim() && String(n.label).trim() !== trimmed)
  if (!childNodes.length) return null
  const rand = childNodes[Math.floor(Math.random() * childNodes.length)]
  return String(rand.label || '').trim() || null
}

/** 当前选中图谱节点：本节点 + 所有下级在练习记录中的得分合计 */
const graphNodeMastery = computed(() => {
  const startId = selectedNodeId.value
  if (!startId || !graphData.value.nodes?.length) {
    return {
      ratio: null,
      score: 0,
      full: 0,
      noData: true,
      attemptCount: 0
    }
  }
  const labels = collectDescendantLabelsFromGraph(startId)
  let score = 0
  let full = 0
  let attemptCount = 0
  for (const item of learningRecordsForGraph.value) {
    const kp = String(item.knowledgePoint || '').trim()
    if (kp && labels.has(kp)) {
      score += Number(item.score || 0)
      full += Number(item.fullScore || 0)
      attemptCount += 1
    }
  }
  if (full <= 0) {
    return { ratio: null, score: 0, full: 0, noData: true, attemptCount }
  }
  return {
    ratio: Math.round((score / full) * 100),
    score,
    full,
    noData: false,
    attemptCount
  }
})

const graphNetworkData = computed(() => {
  const nodes = graphData.value.nodes || []
  const rawEdges = graphData.value.edges || []
  // 前置知识点关系不再展示：过滤掉 label 含“前置”的边
  const edges = rawEdges.filter((edge) => !((edge.label || '').toString().includes('前置')))
  if (!nodes.length) {
    return null
  }

  const nodeMap = new Map(nodes.map((node) => [node.id, node]))
  const childMap = new Map()
  const indegreeMap = new Map(nodes.map((node) => [node.id, 0]))

  for (const edge of edges) {
    if (!childMap.has(edge.source)) {
      childMap.set(edge.source, [])
    }
    childMap.get(edge.source).push(edge.target)
    indegreeMap.set(edge.target, (indegreeMap.get(edge.target) || 0) + 1)
  }

  const root = nodes.find((item) => item.id === 'root') || nodes[0]
  const depthMap = new Map([[root.id, 0]])
  const queue = [root.id]

  while (queue.length) {
    const currentId = queue.shift()
    const currentDepth = depthMap.get(currentId) || 0
    for (const childId of childMap.get(currentId) || []) {
      if (!depthMap.has(childId)) {
        depthMap.set(childId, currentDepth + 1)
        queue.push(childId)
      }
    }
  }

  const styledNodes = nodes.map((node) => {
    const depth = depthMap.get(node.id) ?? 1
    const isRoot = node.id === root.id
    const hasChildren = childMap.has(node.id)
    const color = isRoot
      ? '#24a148'
      : depth === 1
        ? '#c44536'
        : depth === 2
          ? '#2f7ed8'
          : '#9ed9ea'

    return {
      id: node.id,
      name: node.label,
      value: node.label,
      category: Math.min(depth, 3),
      symbolSize: isRoot ? 58 : hasChildren ? 46 : 38,
      draggable: true,
      itemStyle: {
        color,
        borderColor: '#eaf4fb',
        borderWidth: 3,
        shadowBlur: 10,
        shadowColor: 'rgba(33, 59, 89, 0.18)'
      },
      label: {
        show: true,
        color: '#000',
        fontWeight: 500,
        fontSize: isRoot ? 18 : depth === 1 ? 15 : depth === 2 ? 13 : 12,
        lineHeight: 18,
        formatter: node.label
      }
    }
  })

  const styledLinks = edges.map((edge) => {
    const sourceDepth = depthMap.get(edge.source) ?? 0
    const base = {
      source: edge.source,
      target: edge.target,
      value: edge.label || '包含',
      lineStyle: {
        color: sourceDepth === 0 ? '#7aa96b' : sourceDepth === 1 ? '#e1a692' : '#9db9d5',
        width: sourceDepth <= 1 ? 2.2 : 1.6,
        opacity: 0.95,
        curveness: 0.08
      }
    }
    return base
  })

  return {
    nodes: styledNodes,
    links: styledLinks,
    categories: [
      { name: '课程' },
      { name: '一级知识点' },
      { name: '二级知识点' },
      { name: '扩展知识点' }
    ],
    rootId: root.id,
    indegreeMap
  }
})

const learningStats = computed(() => {
  if (!filteredLearningRecords.value.length) {
    return {
      total: 0,
      average: 0,
      mastery: 0
    }
  }

  const totalScore = filteredLearningRecords.value.reduce((sum, item) => sum + item.score, 0)
  const totalFull = filteredLearningRecords.value.reduce((sum, item) => sum + item.fullScore, 0)
  const average = Math.round((totalScore / filteredLearningRecords.value.length) * 10) / 10
  const mastery = totalFull > 0 ? Math.round((totalScore / totalFull) * 100) : 0

  return {
    total: filteredLearningRecords.value.length,
    average,
    mastery
  }
})

const composeTopic = (knowledgePoint) => {
  return `${selectedMajor.value} ${learningContextCourse.value} ${knowledgePoint}`.trim()
}

const ensureCourseSelection = () => {
  if (!joinedCourses.value.includes(selectedCourse.value)) {
    selectedCourse.value = joinedCourses.value[0] || ''
  }
}

const joinCourse = async (courseName) => {
  const course = String(courseName || '').trim()
  if (!course || joinedCourses.value.includes(course)) return
  if (!confirm(`确定要加入课程「${course}」吗？加入后即可学习该课程内容。`)) return
  joinedCourses.value.push(course)
  selectedCourse.value = course
  // 立即落库，避免防抖窗口内退出登录导致未保存
  await persistStudentState(false)
}

/** 已加入：从广场进入学习上下文并打开知识图谱（与个人中心「统计课程」无关） */
const enterCourseFromMarket = async (courseName) => {
  const course = String(courseName || '').trim()
  if (!course || !joinedCourses.value.includes(course)) return
  previewUnjoinedCourse.value = ''
  learningContextCourse.value = course
  currentPage.value = 'graph'
  await persistStudentState(false)
  await nextTick()
  await loadGraph()
}

/** 未加入：仅浏览图谱，不可点击知识点查看资料与生成建议 */
const viewCourseWithoutJoin = async (courseName) => {
  const course = String(courseName || '').trim()
  if (!course || joinedCourses.value.includes(course)) return
  learningContextCourse.value = ''
  previewUnjoinedCourse.value = course
  currentPage.value = 'graph'
  await nextTick()
  await loadGraph()
}

const clearExerciseUiAfterQuittingCurrentCourse = () => {
  generatedQuestion.value = null
  questionError.value = ''
  questionLoading.value = false
  practiceResult.value = null
  practiceError.value = ''
  practiceAnswer.value = ''
  selectedChoiceAnswer.value = ''
  answerImageFile.value = null
  answerImageBase64.value = ''
  resetTestState()
  testLoading.value = false
  examError.value = ''
  if (testForm.value && typeof testForm.value === 'object') {
    testForm.value.selectedPoints = []
  }
}

const quitCourse = async (courseName) => {
  const course = String(courseName || '').trim()
  if (!course || !joinedCourses.value.includes(course)) return
  if (
    !confirm(
      `确定要退出课程「${course}」吗？\n\n退出后将清除该课程下的学习记录、错题收藏等所有本地进度（与服务器同步后也会删除），此操作不可恢复。`
    )
  ) {
    return
  }

  const wasCurrent = selectedCourse.value === course
  if (learningContextCourse.value === course) {
    learningContextCourse.value = ''
    clearExerciseUiAfterQuittingCurrentCourse()
  }
  learningRecords.value = (learningRecords.value || []).filter((item) => item.course !== course)
  wrongBook.value = (wrongBook.value || []).filter((item) => item.course !== course)
  if (wrongBookModalItem.value?.course === course) {
    wrongBookModalItem.value = null
  }

  joinedCourses.value = joinedCourses.value.filter((c) => c !== course)

  if (wasCurrent) {
    selectedCourse.value = joinedCourses.value[0] || ''
    clearExerciseUiAfterQuittingCurrentCourse()
  }

  try {
    await saveStudentState({
      userId: props.currentUser.id,
      major: selectedMajor.value || null,
      courseName: learningContextCourse.value || selectedCourse.value,
      learningRecords: learningRecords.value,
      wrongBook: wrongBook.value,
      joinedCourses: joinedCourses.value
    })
  } catch {
    profileMessage.value = '课程已退出，但同步服务器失败，请稍后刷新或重试。'
  }
}

const escapeHtml = (text) => {
  return String(text || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

const renderLatexText = (text) => {
  const source = String(text || '')
  if (!source.trim()) {
    return ''
  }

  // 支持：
  // - $$...$$ 块公式（多行）
  // - $...$ 公式（允许跨行，部分 AI 输出会在 $..$ 中带换行）
  // - \\(...\\) / \\[...\\] 形式
  const parts = source.split(/(\$\$[\s\S]+?\$\$|\$[\s\S]+?\$|\\\([\s\S]+?\\\)|\\\[[\s\S]+?\\\])/g)

  const normalizeLatex = (s) => {
    // AI 常把箭头写成 ->，KaTeX 不一定能自动识别
    return String(s || '').replace(/-\>/g, '\\\\to')
  }

  const renderNonLatex = (s) => {
    // 先转义再渲染 Markdown 的加粗（**...**）
    return escapeHtml(s).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replaceAll('\n', '<br/>')
  }

  return parts
    .map((part) => {
      if (!part) {
        return ''
      }

      // $$...$$
      if (part.startsWith('$$') && part.endsWith('$$')) {
        const latex = normalizeLatex(part.slice(2, -2))
        return katex.renderToString(latex, { displayMode: true, throwOnError: false })
      }

      // $...$
      if (part.startsWith('$') && part.endsWith('$')) {
        const latex = normalizeLatex(part.slice(1, -1))
        return katex.renderToString(latex, { displayMode: false, throwOnError: false })
      }

      // \(...\)
      if (part.startsWith('\\(') && part.endsWith('\\)')) {
        const latex = normalizeLatex(part.slice(2, -2))
        return katex.renderToString(latex, { displayMode: false, throwOnError: false })
      }

      // \[...\]
      if (part.startsWith('\\[') && part.endsWith('\\]')) {
        const latex = normalizeLatex(part.slice(2, -2))
        return katex.renderToString(latex, { displayMode: true, throwOnError: false })
      }

      return renderNonLatex(part)
    })
    .join('')
}

const loadStudentState = async () => {
  hydrateJoiningCourses = true
  let needsPushJoinedMigration = false
  try {
    const { data } = await fetchStudentState(props.currentUser.id)
    // 回显专业，data.major 为 code。确保已加载专业树后再回显。
    await loadMajorLevel1()
    selectedMajor1.value = ''
    selectedMajor2.value = ''
    selectedMajor3.value = ''
    if (data?.major) {
      const path = findMajorPath(majorLevel1.value, data.major)
      // path 可能为 [lvl1], [lvl1,lvl2], [lvl1,lvl2,lvl3]
      if (path.length >= 1) selectedMajor1.value = path[0]
      if (path.length >= 2) {
        // 确保二级数据已加载
        await loadMajorLevel2(path[0])
        selectedMajor2.value = path[1]
      }
      if (path.length >= 3) {
        await loadMajorLevel3(path[1])
        selectedMajor3.value = path[2]
      }
    }
    // 须先等当前专业下的课程列表加载完成，否则会误把 joinedCourses 过滤为空
    await fetchAvailableCoursesForCurrentMajor()
    const fromServer = Array.isArray(data?.joinedCourses)
      ? data.joinedCourses.filter((x) => typeof x === 'string' && x.trim())
      : []
    const legacyJoined = readLegacyJoinedCoursesFromLocalStorage()
    const mergedJoined = [...new Set([...fromServer, ...legacyJoined])]
    needsPushJoinedMigration = legacyJoined.length > 0
    // 登录恢复时以服务端为准，勿按当前 availableCourses 过滤（专业未回显或列表延迟时否则会整表清空）
    joinedCourses.value = [...new Set(mergedJoined.map((x) => String(x || '').trim()).filter(Boolean))]
    // 个人中心统计课程 + 学习上下文（进入课程）初始与服务端 courseName 对齐
    if (data?.courseName && joinedCourses.value.includes(data.courseName)) {
      selectedCourse.value = data.courseName
      learningContextCourse.value = data.courseName
    } else {
      ensureCourseSelection()
      learningContextCourse.value = ''
    }

    // removed handling of profile bio (学习目标) per UI change

    learningRecords.value = Array.isArray(data.learningRecords) ? data.learningRecords : []
    wrongBook.value = Array.isArray(data.wrongBook) ? data.wrongBook : []
  } catch {
    profileMessage.value = '未读取到历史学习状态，已使用默认配置。'
  } finally {
    hydrateJoiningCourses = false
    stateHydrated.value = true
  }
  if (needsPushJoinedMigration && joinedCourses.value.length) {
    await persistStudentState(false)
  }
}

// 在专业树中查找目标 code 的路径（返回 code 数组，按层级）
const findMajorPath = (majors, targetCode) => {
  if (!Array.isArray(majors)) return []
  for (const m of majors) {
    if (m.code === targetCode) {
      return [m.code]
    }
    if (Array.isArray(m.subfields)) {
      // 递归查找子节点
      const sub = findMajorPath(m.subfields, targetCode)
      if (sub.length) {
        return [m.code, ...sub]
      }
    }
  }
  return []
}

const persistStudentState = async (showMessage = false) => {
  if (!stateHydrated.value) {
    return
  }

  try {
    await saveStudentState({
      userId: props.currentUser.id,
      major: selectedMajor.value || null,
      courseName: learningContextCourse.value || selectedCourse.value,
      learningRecords: learningRecords.value,
      wrongBook: wrongBook.value,
      joinedCourses: joinedCourses.value
    })
    if (showMessage) {
      profileMessage.value = '个人信息已保存到服务器。'
    }
  } catch {
    if (showMessage) {
      profileMessage.value = '保存失败，请稍后重试。'
    }
  }
}

const schedulePersistStudentState = () => {
  if (!stateHydrated.value) {
    return
  }
  if (stateSaveTimer) {
    clearTimeout(stateSaveTimer)
  }
  stateSaveTimer = setTimeout(() => {
    persistStudentState(false)
  }, 350)
}

const loadLearningSuggestionsFor = async (pointName) => {
  const reqId = ++suggestionReqId
  suggestionLoading.value = true
  suggestionError.value = ''

  const kp = pointName || selectedKnowledgePoint.value || ''
  if (!learningContextCourse.value || !kp) {
    learningSuggestions.value = []
    suggestionLoading.value = false
    return
  }

  try {
    const resp = await fetchLearningSuggestions({
      topic: learningContextCourse.value,
      knowledgePoint: kp
    })
    const payload = resp && resp.data ? resp.data : resp
    const suggestions = Array.isArray(payload?.suggestions) ? payload.suggestions : []
    // 只处理最新一次请求的结果（避免快速切换节点时乱序覆盖）
    if (reqId === suggestionReqId) {
      learningSuggestions.value = suggestions
    }
  } catch (err) {
    if (reqId === suggestionReqId) {
      suggestionError.value = err?.response?.data?.message || '学习建议生成失败，请稍后重试。'
      learningSuggestions.value = []
    }
  } finally {
    if (reqId === suggestionReqId) {
      suggestionLoading.value = false
    }
  }
}

const loadMajorRelevanceFor = async (pointName) => {
  const reqId = ++relevanceReqId
  relevanceLoading.value = true
  relevanceError.value = ''

  const kp = pointName || selectedKnowledgePoint.value || ''
  const majorText = selectedMajorDisplay.value || selectedMajor.value || ''
  if (!learningContextCourse.value || !kp || !majorText) {
    majorRelevance.value = {
      scoreLevel: null,
      summary: '',
      relatedContents: [],
      lowRelevanceReason: ''
    }
    relevanceLoading.value = false
    return
  }

  try {
    const resp = await fetchMajorRelevance({
      topic: learningContextCourse.value,
      knowledgePoint: kp,
      major: majorText
    })
    const payload = resp && resp.data ? resp.data : resp
    if (reqId === relevanceReqId) {
      majorRelevance.value = {
        scoreLevel: Number(payload?.scoreLevel) || null,
        summary: payload?.summary || '',
        relatedContents: Array.isArray(payload?.relatedContents) ? payload.relatedContents : [],
        lowRelevanceReason: payload?.lowRelevanceReason || ''
      }
    }
  } catch (err) {
    if (reqId === relevanceReqId) {
      relevanceError.value = err?.response?.data?.message || '专业关联度分析生成失败，请稍后重试。'
      majorRelevance.value = {
        scoreLevel: null,
        summary: '',
        relatedContents: [],
        lowRelevanceReason: ''
      }
    }
  } finally {
    if (reqId === relevanceReqId) {
      relevanceLoading.value = false
    }
  }
}

const loadMaterialsByKnowledgePoint = async (pointName) => {
  if (!learningContextCourse.value || !pointName) {
    materials.value = []
    return
  }
  try {
    const resp = await fetchMaterialsByKnowledgePoint(learningContextCourse.value, pointName, false)
    materials.value = resp.data
  } catch {
    materials.value = []
  }
}

const applyDiscussionDeepLinkFromRoute = async () => {
  const rawDc = route.query.dc
  const rawDp = route.query.dp
  if (!rawDc || !rawDp) return
  const dc = Array.isArray(rawDc) ? rawDc[0] : rawDc
  const dp = Array.isArray(rawDp) ? rawDp[0] : rawDp
  const courseName = String(dc || '').trim()
  const pointName = String(dp || '').trim()
  const rawPost = route.query.dpost
  const dpostRaw = Array.isArray(rawPost) ? rawPost[0] : rawPost
  const postNum = dpostRaw != null && dpostRaw !== '' ? Number(dpostRaw) : NaN

  const nextQ = { ...route.query }
  delete nextQ.dc
  delete nextQ.dp
  delete nextQ.dpost

  if (!joinedCourses.value.includes(courseName)) {
    discussionFocusPostId.value = null
    pendingGraphPointLabel.value = ''
    try {
      await router.replace({ path: route.path, query: nextQ })
    } catch {
      /* ignore */
    }
    return
  }

  discussionFocusPostId.value = Number.isFinite(postNum) ? postNum : null
  pendingGraphPointLabel.value = pointName
  previewUnjoinedCourse.value = ''
  learningContextCourse.value = courseName
  selectedCourse.value = courseName
  currentPage.value = 'graph'
  try {
    await router.replace({ path: route.path, query: nextQ })
  } catch {
    /* ignore */
  }
  await nextTick()
  await loadGraph()
}

const loadGraph = async () => {
  const topic = learningContextCourse.value || previewUnjoinedCourse.value
  const emptyGraph = () => {
    graphData.value = { title: '知识图谱', nodes: [], edges: [], suggestions: [] }
    selectedNodeId.value = ''
    selectedKnowledgePoint.value = ''
    materials.value = []
    learningSuggestions.value = []
    majorRelevance.value = {
      scoreLevel: null,
      summary: '',
      relatedContents: [],
      lowRelevanceReason: ''
    }
  }
  if (!topic) {
    emptyGraph()
    return
  }
  const isPreview = Boolean(previewUnjoinedCourse.value)
  if (!isPreview) {
    if (!learningContextCourse.value || !joinedCourses.value.includes(learningContextCourse.value)) {
      emptyGraph()
      return
    }
  }
  graphLoading.value = true
  graphError.value = ''
  try {
    const { data } = await fetchKnowledgeGraph({ topic })
    graphData.value = {
      title: data.title || '知识图谱',
      nodes: Array.isArray(data.nodes) ? data.nodes : [],
      edges: Array.isArray(data.edges) ? data.edges : [],
      suggestions: Array.isArray(data.suggestions) ? data.suggestions : []
    }
    learningSuggestions.value = Array.isArray(graphData.value.suggestions) ? graphData.value.suggestions : []

    const rootNode = graphData.value.nodes.find((n) => n.id === 'root')
    const pendingLabel = pendingGraphPointLabel.value
    const applyRootSelection = async (label) => {
      selectedKnowledgePoint.value = label
      if (currentPage.value === 'graph' && !isPreview) {
        await loadMaterialsByKnowledgePoint(label)
        void loadLearningSuggestionsFor(label)
        void loadMajorRelevanceFor(label)
      } else if (isPreview) {
        materials.value = []
        learningSuggestions.value = []
        majorRelevance.value = {
          scoreLevel: null,
          summary: '',
          relatedContents: [],
          lowRelevanceReason: ''
        }
      }
    }
    if (pendingLabel) {
      pendingGraphPointLabel.value = ''
      const target = graphData.value.nodes.find(
        (n) => String(n.label || '').trim() === String(pendingLabel).trim()
      )
      if (target?.label) {
        selectedNodeId.value = target.id
        graphClickedNodeCategory.value = typeof target.category === 'number' ? target.category : null
        await applyRootSelection(target.label)
      } else if (rootNode?.label) {
        selectedNodeId.value = 'root'
        graphClickedNodeCategory.value = typeof rootNode.category === 'number' ? rootNode.category : null
        await applyRootSelection(rootNode.label)
      }
    } else if (rootNode?.label) {
      selectedNodeId.value = 'root'
      graphClickedNodeCategory.value = typeof rootNode.category === 'number' ? rootNode.category : null
      await applyRootSelection(rootNode.label)
    }
    await nextTick()
    renderGraphChart()
  } catch (err) {
    graphError.value = err?.response?.data?.message || '知识图谱加载失败，请稍后重试。'
  } finally {
    graphLoading.value = false
  }
}

watch(
  learningContextCourse,
  () => {
    kpCascade1.value = ''
    kpCascade2.value = ''
    kpCascade3.value = ''
    loadExamPoints()
    schedulePersistStudentState()
    if (currentPage.value === 'graph') {
      void loadGraph()
    }
  },
  { immediate: true }
)

const renderGraphChart = () => {
  if (!graphChartRef.value || !graphNetworkData.value) {
    return
  }

  if (graphChart && graphChart.getDom() !== graphChartRef.value) {
    graphChart.dispose()
    graphChart = null
  }

  if (!graphChart) {
    graphChart = echarts.init(graphChartRef.value)
    graphChart.on('click', (params) => {
      if (previewUnjoinedCourse.value) {
        return
      }
      if (params?.data?.id) {
        selectedNodeId.value = params.data.id
        const name = params.data.name
        selectedKnowledgePoint.value = name
        graphClickedNodeCategory.value = typeof params?.data?.category === 'number' ? params.data.category : null
        void loadMaterialsByKnowledgePoint(name)
        void loadLearningSuggestionsFor(name)
        void loadMajorRelevanceFor(name)
      }
    })
  }

  graphChart.setOption({
    tooltip: {
      trigger: 'item',
      triggerOn: 'mousemove',
      formatter: (params) => {
        if (params.dataType === 'edge') {
          return `${params.data.source} -> ${params.data.target}`
        }
        return params.data?.name || ''
      }
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        data: graphNetworkData.value.nodes,
        links: graphNetworkData.value.links,
        categories: graphNetworkData.value.categories,
        roam: true,
        draggable: true,
        edgeSymbol: ['none', 'none'],
        force: {
          repulsion: 520,
          gravity: 0.06,
          edgeLength: 120,
          friction: 0.12,
          layoutAnimation: true
        },
        lineStyle: {
          width: 2,
          opacity: 0.9
        },
        label: {
          position: 'inside',
          color: '#000'
        },
        emphasis: {
          focus: 'adjacency',
          scale: 1.08,
          lineStyle: {
            width: 3
          }
        },
        animationDurationUpdate: 600,
        animationEasingUpdate: 'quinticInOut'
      }
    ]
  })
}

const removeWrongItem = (id) => {
  if (!id) return
  if (!confirm('确定删除这条错题记录吗？')) return
  wrongBook.value = wrongBook.value.filter((item) => item.id !== id)
  if (wrongBookModalItem.value?.id === id) {
    wrongBookModalItem.value = null
  }
  schedulePersistStudentState()
}

/** 错题本卡片预览：弱化公式占位，避免格子内过长 */
const wrongBookQuestionPreview = (raw) => {
  let s = String(raw || '')
    .replace(/\$\$[\s\S]*?\$\$/g, '〔公式〕')
    .replace(/\$[^$\n]+?\$/g, '〔式〕')
    .replace(/\s+/g, ' ')
    .trim()
  if (s.length > 72) s = `${s.slice(0, 72)}…`
  return s || '（无题干）'
}

const wrongBookModalItem = ref(null)
const openWrongBookModal = (item) => {
  wrongBookModalItem.value = item || null
}
const closeWrongBookModal = () => {
  wrongBookModalItem.value = null
}

const announcements = ref([])
const annLoading = ref(false)
const annError = ref('')

const loadStudentAnnouncements = async () => {
  annLoading.value = true
  annError.value = ''
  try {
    const { data } = await fetchAnnouncements()
    announcements.value = Array.isArray(data) ? data : []
  } catch (e) {
    annError.value = e?.response?.data?.message || '加载公告失败。'
    announcements.value = []
  } finally {
    annLoading.value = false
  }
}

const formatAnnTime = (iso) => {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return String(iso)
  }
}

watch(currentPage, (v) => {
  if (v !== 'review') closeWrongBookModal()
})

const removeLearningRecord = (id) => {
  if (!id) return
  if (!confirm('确定删除这条学习记录吗？')) return
  learningRecords.value = learningRecords.value.filter((item) => item.id !== id)
  schedulePersistStudentState()
}

const editProfileVisible = ref(false)
const editProfileForm = ref({ username: '', email: '', major1: '', major2: '', major3: '' })

const openEditProfile = () => {
  editProfileForm.value.username = props.currentUser.username || profileForm.value.username || ''
  editProfileForm.value.email = props.currentUser.email || profileForm.value.email || ''
  // 回显当前已选择的专业层级
  editProfileForm.value.major1 = selectedMajor1.value || ''
  editProfileForm.value.major2 = selectedMajor2.value || ''
  editProfileForm.value.major3 = selectedMajor3.value || ''
  editProfileVisible.value = true
}

const handleSaveProfile = async () => {
  // 更新本地展示值
  profileForm.value.username = editProfileForm.value.username
  profileForm.value.email = editProfileForm.value.email

  // 回写专业选择到主状态，并持久化到学生状态接口
  selectedMajor1.value = editProfileForm.value.major1 || ''
  if (selectedMajor1.value) {
    await loadMajorLevel2(selectedMajor1.value)
  } else {
    majorLevel2.value = []
    majorLevel3.value = []
  }
  selectedMajor2.value = editProfileForm.value.major2 || ''
  if (selectedMajor2.value) {
    await loadMajorLevel3(selectedMajor2.value)
  } else {
    majorLevel3.value = []
  }
  selectedMajor3.value = editProfileForm.value.major3 || ''
  await persistStudentState(true)

  // 尝试同步到后端
  try {
    const payload = {
      userId: props.currentUser.id,
      username: editProfileForm.value.username,
      email: editProfileForm.value.email
    }
    const resp = await updateUser(payload)
    editProfileVisible.value = false
    profileMessage.value = resp?.data?.message || '已更新用户信息'
    // 更新 localStorage，以便顶栏和其他组件能读取到最新用户信息
    const updatedUser = resp?.data?.user
      ? resp.data.user
      : { ...props.currentUser, username: profileForm.value.username, email: profileForm.value.email }
    try { localStorage.setItem('currentUser', JSON.stringify(updatedUser)) } catch (e) {}
    // 向父组件传递最新用户信息以便上层刷新
    emit('update-user', {
      username: updatedUser.username,
      email: updatedUser.email,
      ...(updatedUser.workId !== undefined && updatedUser.workId !== null ? { workId: updatedUser.workId } : {})
    })
  } catch (err) {
    // 保持本地更新，向用户展示错误信息
    editProfileVisible.value = false
    profileMessage.value = err?.response?.data?.message || '同步用户信息到服务器失败，请稍后重试。'
    // 即使后端同步失败，也更新 localStorage 显示最新输入，避免用户界面和输入不一致
    const fallbackUser = { ...props.currentUser, username: profileForm.value.username, email: profileForm.value.email }
    try { localStorage.setItem('currentUser', JSON.stringify(fallbackUser)) } catch (e) {}
    emit('update-user', {
      username: profileForm.value.username,
      email: profileForm.value.email,
      ...(props.currentUser.workId ? { workId: props.currentUser.workId } : {})
    })
  }
}

const changePasswordVisible = ref(false)
const openPasswordPage = () => {
  changePasswordVisible.value = true
}

ensureCourseSelection()

watch(selectedCourse, () => {
  schedulePersistStudentState()
})

watch(joinedCoursesSearch, () => {
  joinedCoursesPage.value = 1
})

watch(
  [currentPage, availableCourses],
  async () => {
    if (currentPage.value !== 'courses') return
    await loadTeachersForMarketCourses()
  },
  { deep: true }
)

watch([selectedMajor1, selectedMajor2, selectedMajor3], () => {
  if (currentPage.value === 'graph' && selectedKnowledgePoint.value) {
    void loadMajorRelevanceFor(selectedKnowledgePoint.value)
  }
})

// removed watcher for profileForm.bio (learning goal removed)

watch(
  currentPage,
  (v) => {
    if (v === 'announcements') void loadStudentAnnouncements()
  },
  { immediate: true }
)

watch(
  () => currentPage.value,
  async (value) => {
    if (value === 'review') {
      await loadSavedExams()
    }
    if (value === 'graph') {
      if (!canShowGraphPage.value) return
      if (!graphData.value.nodes?.length) {
        await loadGraph()
      } else {
        await nextTick()
        renderGraphChart()
        const kp =
          selectedKnowledgePoint.value ||
          graphData.value.nodes.find((n) => n.id === 'root')?.label ||
          ''
        if (kp && !previewUnjoinedCourse.value) {
          await loadMaterialsByKnowledgePoint(kp)
          void loadLearningSuggestionsFor(kp)
          void loadMajorRelevanceFor(kp)
        }
      }
      await nextTick()
      graphChart?.resize()
      return
    }

    if (graphChart) {
      graphChart.dispose()
      graphChart = null
    }
  }
)

watch(
  () => route.query.dc,
  () => {
    void applyDiscussionDeepLinkFromRoute()
  }
)

onMounted(async () => {
  await loadStudentState()
  const hadDiscussionDeepLink = Boolean(route.query.dc && route.query.dp)
  await applyDiscussionDeepLinkFromRoute()
  if (!hadDiscussionDeepLink && (canStudyCurrentCourse.value || previewUnjoinedCourse.value)) loadGraph()
  await loadSavedExams()
  window.addEventListener('resize', handleWindowResize)
})

onBeforeUnmount(() => {
  if (stateSaveTimer) {
    clearTimeout(stateSaveTimer)
    stateSaveTimer = null
  }
  window.removeEventListener('resize', handleWindowResize)
  if (graphChart) {
    graphChart.dispose()
    graphChart = null
  }
})

const handleWindowResize = () => {
  graphChart?.resize()
}

const downloadExam = (id, type) => {
  if (!id) return
  window.open('/api/exams/' + id + '/download?type=' + type)
}

const renderExamPdfs = async (id) => {
  if (!id) return
  try {
    const { data } = await http.post('/exams/' + id + '/render')
    await loadSavedExams()
    if (data && data.mdPaper) downloadExam(id, 'md_paper')
    if (data && data.mdAnswer) downloadExam(id, 'md_answer')
  } catch (e) {
    console.error('renderExamPdfs failed', e)
    examError.value = '服务器生成 Markdown 文件失败，请稍后重试。'
  }
}



const confirmDeleteExam = async (id) => {
  if (!id) return
  if (!confirm('确定要删除该已保存试卷吗？此操作不可恢复。')) return
  try {
    await deleteExam(id)
    await loadSavedExams()
  } catch (e) {
    examError.value = '删除失败，请稍后重试。'
  }
}
</script>

<template>
    <StudentHome
      v-if="currentPage === 'home'"
      :user-initial="userInitial"
      :profile-form="profileForm"
      :current-user="currentUser"
      :selected-course="selectedCourse"
      :joined-courses="joinedCourses"
      :selected-major-display="selectedMajorDisplay"
      :learning-stats="learningStats"
      :filtered-wrong-book-count="filteredWrongBook.length"
      :profile-message="profileMessage"
      @update:selected-course="(v) => (selectedCourse = v)"
      @edit-profile="openEditProfile"
      @change-password="openPasswordPage"
      @logout="emit('logout')"
    />

    <!-- 修改密码已改为模态窗口 -->

    <StudentCourses
      v-if="currentPage === 'courses'"
      :joined-courses="joinedCourses"
      :joined-courses-search="joinedCoursesSearch"
      :joined-courses-page="joinedCoursesPage"
      :market-total-pages="marketTotalPages"
      :paged-market-courses="pagedMarketCourses"
      :teachers-by-course-loading="teachersByCourseLoading"
      :format-teachers-for-course="formatTeachersForCourse"
      :state-hydrated="stateHydrated"
      @update:joined-courses-search="(v) => (joinedCoursesSearch = v)"
      @update:joined-courses-page="(p) => (joinedCoursesPage = p)"
      @join="joinCourse"
      @view="viewCourseWithoutJoin"
      @enter="enterCourseFromMarket"
      @quit="quitCourse"
    />

    <section v-if="currentPage === 'graph'" class="panel-stack">
      <StudentGraph
        :can-show-graph-page="canShowGraphPage"
        :is-unjoined-preview-mode="isUnjoinedPreviewMode"
        :graph-data="graphData"
        :graph-loading="graphLoading"
        :graph-error="graphError"
        :graph-node-mastery="graphNodeMastery"
        :learning-suggestions="learningSuggestions"
        :suggestion-loading="suggestionLoading"
        :suggestion-error="suggestionError"
        :major-relevance="majorRelevance"
        :relevance-loading="relevanceLoading"
        :relevance-error="relevanceError"
        :relevance-label="relevanceLabel"
        :materials="materials"
        :practice-test-allowed="practiceTestAllowed"
        @go-courses="currentPage = 'courses'"
        @refresh-graph="loadGraph"
        @enter-test="enterFixedTestFromGraph"
      >
        <article class="result-card">
          <h3 class="panel-title">{{ graphData.title }}</h3>
          <div class="inline-form">
            <button :disabled="graphLoading" @click="loadGraph">{{ graphLoading ? '加载中...' : '刷新图谱' }}</button>
          </div>
          <p v-if="graphError" class="error-text">{{ graphError }}</p>
          <div ref="graphChartRef" style="width: 100%; height: 420px;"></div>
        </article>

        <article v-if="selectedNode && !isUnjoinedPreviewMode" class="result-card">
          <h3>{{ selectedNode.label }}</h3>
          <div style="margin-bottom:10px;display:flex;flex-wrap:wrap;gap:8px;align-items:center;">
            <button
              type="button"
              class="match-button"
              :disabled="!practiceTestAllowed"
              @click="enterFixedTestFromGraph"
            >
              进入测试
            </button>
            <span v-if="!practiceTestAllowed" class="panel-subtitle" style="margin-left:4px;">
              测试需从课程名、章节名或节进入；当前为更深层级知识点时，请回到上一级。
            </span>
          </div>
          <p class="panel-subtitle" style="margin-bottom:10px">
            自定义组卷请使用顶部导航「组卷」，可任选课程与每题的题型、知识点。
          </p>
          <div style="margin-bottom:14px">
            <h3>掌握程度</h3>
            <p v-if="graphNodeMastery.noData" class="panel-subtitle">
              暂无该知识点及其下级相关的练习记录。
            </p>
            <template v-else>
              <p class="panel-subtitle">
                <strong style="font-size:1.15em">{{ graphNodeMastery.ratio }}%</strong>
                <span>（{{ graphNodeMastery.score }} / {{ graphNodeMastery.full }} 分，{{ graphNodeMastery.attemptCount }} 次练习）</span>
              </p>
            </template>
          </div>
          <div>
            <h3>学习建议</h3>
            <p v-if="suggestionLoading" class="panel-subtitle">正在生成学习建议...</p>
            <p v-else-if="suggestionError" class="error-text">{{ suggestionError }}</p>
            <ul v-else>
              <li v-for="item in learningSuggestions" :key="item">{{ item }}</li>
              <li v-if="!learningSuggestions.length" class="panel-subtitle">暂无建议。</li>
            </ul>
          </div>
          <div style="margin-top:12px;">
            <h3>专业关联度分析</h3>
            <p v-if="relevanceLoading" class="panel-subtitle">正在分析该知识点与当前专业的关联度...</p>
            <p v-else-if="relevanceError" class="error-text">{{ relevanceError }}</p>
            <div v-else>
              <div v-if="majorRelevance.scoreLevel" class="relevance-meter-wrap">
                <div class="relevance-meter">
                  <span
                    v-for="i in 5"
                    :key="'relevance-dot-' + i"
                    class="relevance-dot"
                    :class="{ active: i <= majorRelevance.scoreLevel }"
                  />
                </div>
                <div class="relevance-meter-text">
                  <strong>关联度等级：</strong>{{ majorRelevance.scoreLevel }} / 5
                  <span v-if="relevanceLabel">（{{ relevanceLabel }}）</span>
                </div>
              </div>
              <p v-if="majorRelevance.summary" class="panel-subtitle">{{ majorRelevance.summary }}</p>
              <div v-if="majorRelevance.relatedContents?.length">
                <p><strong>相关内容：</strong></p>
                <ul>
                  <li v-for="item in majorRelevance.relatedContents" :key="item">{{ item }}</li>
                </ul>
              </div>
              <p v-if="majorRelevance.lowRelevanceReason" class="panel-subtitle">
                <strong>低关联说明：</strong>{{ majorRelevance.lowRelevanceReason }}
              </p>
              <p v-if="!majorRelevance.scoreLevel" class="panel-subtitle">请选择已设置专业后查看分析结果。</p>
            </div>
          </div>
          <div style="margin-top:12px;">
            <h3>相关资料</h3>
            <div v-if="!materials.length" class="panel-subtitle">当前知识点暂无资料。</div>
            <table v-else class="data-table">
              <thead>
                <tr>
                  <th>标题</th>
                  <th>描述</th>
                  <th>文件名</th>
                  <th>上传者</th>
                  <th>时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="m in materials" :key="m.id">
                  <td>{{ m.title }}</td>
                  <td>{{ m.description || '-' }}</td>
                  <td>{{ m.fileName || '-' }}</td>
                  <td>{{ m.teacherName || '-' }}</td>
                  <td>{{ m.createdAt ? new Date(m.createdAt).toLocaleString() : '-' }}</td>
                  <td>
                    <a :href="`/api/materials/${m.id}/download`" target="_blank">下载</a>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <KnowledgePointDiscussion
            :course-name="learningContextCourse"
            :point-name="selectedKnowledgePoint"
            :current-user-id="currentUser?.id"
            :user-role="currentUser?.role"
            :focus-post-id="discussionFocusPostId"
            :disabled="isUnjoinedPreviewMode"
          />
        </article>
      </StudentGraph>
    </section>

    <StudentPaperComposer
      v-if="currentPage === 'paper'"
      :joined-courses="joinedCourses"
      :selected-major="selectedMajor"
      :render-latex-text="renderLatexText"
      :refresh-saved-exams="loadSavedExams"
      @go-courses="currentPage = 'courses'"
    />

    <StudentExercise
      v-if="currentPage === 'exercise'"
      v-model:teacher-kp-test-answers="teacherKpTestAnswers"
      :can-study-current-course="canStudyCurrentCourse"
      :selected-knowledge-point="selectedKnowledgePoint"
      :question-form="questionForm"
      :test-loading="testLoading"
      :test-error="testError"
      :test-questions="testQuestions"
      :test-submitted="testSubmitted"
      :test-result="testResult"
      :test-answers="testAnswers"
      :teacher-kp-test="teacherKpTest"
      :teacher-kp-test-loading="teacherKpTestLoading"
      :teacher-kp-test-error="teacherKpTestError"
      :teacher-kp-test-submitted="teacherKpTestSubmitted"
      :teacher-kp-test-result="teacherKpTestResult"
      :teacher-kp-test-submitting="teacherKpTestSubmitting"
      :generate-test="generateTest"
      :submit-test="submitTest"
      :submit-teacher-kp-test="submitTeacherKpTest"
      :render-latex-text="renderLatexText"
      :parse-option-letter="parseOptionLetter"
      :parse-option-text="parseOptionText"
      :resolve-answer-text="resolveAnswerText"
      @go-courses="currentPage = 'courses'"
    />

    <StudentReview
      v-if="currentPage === 'review'"
      :can-study-current-course="canStudyCurrentCourse"
      :filtered-wrong-book-for-learning-page="filteredWrongBookForLearningPage"
      :filtered-learning-records-for-learning-page="filteredLearningRecordsForLearningPage"
      :saved-exams="savedExams"
      :exam-error="examError"
      :wrong-book-modal-item="wrongBookModalItem"
      :render-latex-text="renderLatexText"
      :wrong-book-question-preview="wrongBookQuestionPreview"
      :open-wrong-book-modal="openWrongBookModal"
      :close-wrong-book-modal="closeWrongBookModal"
      :remove-wrong-item="removeWrongItem"
      :remove-learning-record="removeLearningRecord"
      :confirm-delete-exam="confirmDeleteExam"
      :download-exam="downloadExam"
      :render-exam-pdfs="renderExamPdfs"
      @go-courses="currentPage = 'courses'"
    />

    <StudentAnnouncements
      v-if="currentPage === 'announcements'"
      :ann-loading="annLoading"
      :ann-error="annError"
      :announcements="announcements"
      :format-ann-time="formatAnnTime"
    />

    <StudentEditProfileModal
      :visible="editProfileVisible"
      :edit-profile-form="editProfileForm"
      :major-level1="majorLevel1"
      :major-level2="majorLevel2"
      :major-level3="majorLevel3"
      :on-edit-major1-change="onEditMajor1Change"
      :on-edit-major2-change="onEditMajor2Change"
      :on-save="handleSaveProfile"
      @close="editProfileVisible = false"
    />

    <StudentChangePasswordModal
      :visible="changePasswordVisible"
      :current-user="currentUser"
      @close="changePasswordVisible = false"
    />
    <AiAssistantWidget role="student" :current-user="currentUser" />
</template>

<style src="./student-portal.css"></style>
