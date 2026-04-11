<script setup>
import StudentGraph from './StudentGraph.vue'
import StudentExercise from './StudentExercise.vue'
import StudentTeacherTest from './StudentTeacherTest.vue'
import StudentPaperComposer from './StudentPaperComposer.vue'
import StudentReview from './StudentReview.vue'
import StudentCourses from './StudentCourses.vue'
import CourseDetailPanel from './CourseDetailPanel.vue'
import StudentHome from './StudentHome.vue'
import StudentEditProfileModal from './StudentEditProfileModal.vue'
import StudentChangePasswordModal from './StudentChangePasswordModal.vue'
import StudentGraphLearningPanel from './StudentGraphLearningPanel.vue'
import { useStudentGeneratedTest } from '../composables/useStudentGeneratedTest'
import { useStudentTeacherKpTest } from '../composables/useStudentTeacherKpTest'
import { useStudentPersistState } from '../composables/useStudentPersistState'
import { useStudentWrongDrill } from '../composables/useStudentWrongDrill'
import { buildGraphNetworkData } from '../utils/studentGraphNetwork'
import { collectDescendantLabelsFromGraph } from '../utils/studentGraphTraversal'
import { renderLatexText } from '../utils/renderLatexHtml'
import { wrongBookQuestionPreview } from '../utils/studentWrongBookUi'
import {
  parseOptionLetter,
  parseOptionText,
  resolveAnswerText,
  unescapeNewlinesSafe,
  wrongBookChoiceLetters
} from '../utils/studentTestAnswerUtils'

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
// App.vue 会把 loginSuccess 监听透传到 router-view 下的组件；这里声明以消除 Vue 警告
const emit = defineEmits(['navigate', 'logout', 'update-user', 'loginSuccess'])

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
const answerImageFile = ref(null)
const answerImageBase64 = ref('')
const selectedChoiceAnswer = ref('')
const practiceResult = ref(null)
const normalizeStudentPage = (p) => {
  const raw = String(p || '').trim()
  const allowed = new Set(['home', 'courses', 'course-detail', 'graph', 'paper', 'exercise', 'teacher-test', 'review'])
  return allowed.has(raw) ? raw : 'home'
}
const currentPage = ref(normalizeStudentPage(props.activePage || 'home'))
const effectivePage = computed(() =>
  normalizeStudentPage(route.params.page || props.activePage || currentPage.value || 'home')
)

// 监听外部 activePage prop，驱动内部 currentPage 切换
watch(
  () => props.activePage,
  (val) => {
    const next = normalizeStudentPage(val)
    if (next !== currentPage.value) {
      currentPage.value = next
    }
  }
)

// 以路由为准兜底：避免 props 同步不到导致页面空白
watch(
  () => route.params.page,
  (val) => {
    const next = normalizeStudentPage(val || 'home')
    if (next !== currentPage.value) {
      currentPage.value = next
    }
  },
  { immediate: true }
)
const learningRecords = ref([])
const wrongBook = ref([])
/** 从服务器恢复学生状态期间：不因 availableCourses 尚未就绪而裁剪 joinedCourses */
const hydrateJoiningCourses = ref(false)
const dimensionScores = ref(null)
const dimensionScoresLoading = ref(false)
const dimensionScoresError = ref('')
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AiAssistantWidget from './AiAssistantWidget.vue'
import {
  fetchKnowledgeGraph,
  saveStudentState,
  fetchResourcesByKnowledgePoint,
  fetchResourceProgress,
  markResourceComplete,
  updateUser,
  listKnowledgePoints,
  fetchExams,
  deleteExam,
  fetchLearningSuggestions,
  fetchMajorRelevance,
  saveExam,
  listCoursesByMajor,
  listTeachersForCourses,
  listCourseCatalog,
  getCourseDetail
} from '../api/client'
const resources = ref({ materials: [], tests: [], totalCount: 0 })
const resourcesLoading = ref(false)
const resourcesError = ref('')
const completedResourceKeys = ref([])
const courseProgress = ref(null) // { total, completed, percent }
const selectedKnowledgePoint = ref('')

import { http } from '../api/client'
const joinedCourses = ref([])
const myCourseCatalog = ref([])
const courseDetail = ref(null)
const courseDetailLoading = ref(false)
const courseDetailError = ref('')
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

const courseNamesForTeacherLookup = () => {
  const s = new Set()
  for (const c of availableCourses.value || []) {
    const t = String(c || '').trim()
    if (t) s.add(t)
  }
  for (const c of joinedCourses.value || []) {
    const t = String(c || '').trim()
    if (t) s.add(t)
  }
  for (const it of myCourseCatalog.value || []) {
    const t = String(it?.courseName || '').trim()
    if (t) s.add(t)
  }
  return [...s]
}

const loadTeachersForMarketCourses = async () => {
  const names = courseNamesForTeacherLookup()
  if (!names.length) {
    teachersByCourse.value = {}
    return
  }
  teachersByCourseLoading.value = true
  try {
    const { data } = await listTeachersForCourses(names)
    teachersByCourse.value = data && typeof data === 'object' ? data : {}
  } catch {
    teachersByCourse.value = {}
  } finally {
    teachersByCourseLoading.value = false
  }
}

const formatTeachersForCourse = (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return ''
  const list = teachersByCourse.value[cn]
  if (!Array.isArray(list) || !list.length) return '暂无授课教师信息'
  return list.map((t) => t?.username || '').filter(Boolean).join('、') || '暂无授课教师信息'
}

const loadMyCourseCatalog = async () => {
  if (!props.currentUser?.id) return
  try {
    const { data } = await listCourseCatalog(props.currentUser.id)
    myCourseCatalog.value = Array.isArray(data?.items) ? data.items : []
  } catch {
    myCourseCatalog.value = []
  }
}

const loadCourseDetail = async (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) {
    courseDetail.value = null
    return
  }
  courseDetailLoading.value = true
  courseDetailError.value = ''
  try {
    const { data } = await getCourseDetail(cn, props.currentUser?.id)
    courseDetail.value = data || null
    try {
      const { data: tea } = await listTeachersForCourses([cn])
      if (tea && typeof tea === 'object') {
        teachersByCourse.value = { ...teachersByCourse.value, ...tea }
      }
    } catch {
      /* ignore */
    }
  } catch (e) {
    courseDetailError.value = e?.response?.data?.message || '加载课程详情失败'
    courseDetail.value = null
  } finally {
    courseDetailLoading.value = false
  }
}

const openCourseDetailFromMyCourses = async (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return
  currentPage.value = 'course-detail'
  await router.push({ path: '/student/course-detail', query: { course: cn } })
  await loadCourseDetail(cn)
}
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

const ensureCourseSelection = () => {
  if (!joinedCourses.value.includes(selectedCourse.value)) {
    selectedCourse.value = joinedCourses.value[0] || ''
  }
}

const {
  loadStudentState,
  persistStudentState,
  schedulePersistStudentState,
  scheduleRefreshDimensionScores,
  loadDimensionScores
} = useStudentPersistState({
  getUserId: () => props.currentUser?.id,
  stateHydrated,
  profileMessage,
  learningRecords,
  wrongBook,
  joinedCourses,
  selectedCourse,
  learningContextCourse,
  selectedMajor1,
  selectedMajor2,
  selectedMajor3,
  majorLevel1,
  selectedMajor,
  loadMajorLevel1,
  loadMajorLevel2,
  loadMajorLevel3,
  fetchAvailableCoursesForCurrentMajor,
  readLegacyJoinedCoursesFromLocalStorage,
  ensureCourseSelection,
  dimensionScores,
  dimensionScoresLoading,
  dimensionScoresError,
  hydrateJoiningCourses
})

watch([selectedMajor1, selectedMajor2, selectedMajor3], async () => {
  const appliedReqId = await fetchAvailableCoursesForCurrentMajor()
  if (appliedReqId !== majorCoursesReqId) return
  if (hydrateJoiningCourses.value) return
  if (!availableCourses.value.length) {
    ensureCourseSelection()
    return
  }
  joinedCourses.value = joinedCourses.value.filter((c) => availableCourses.value.includes(c))
  ensureCourseSelection()
  schedulePersistStudentState()
}, { immediate: true })

const {
  wrongDrillCourse,
  wrongDrillSession,
  wrongDrillError,
  wrongDrillSubmitting,
  wrongDrillCourseOptions,
  inferWrongBookQuestionType,
  setWrongDrillCourse,
  startWrongDrill,
  cancelWrongDrill,
  submitWrongDrill
} = useStudentWrongDrill({
  wrongBook,
  learningRecords,
  joinedCourses,
  selectedMajorDisplay,
  selectedMajor,
  effectivePage,
  schedulePersistStudentState
})

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

// 放开限制：任意层级知识点都可直接进入测试
const practiceTestAllowed = computed(() => true)

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
const enterFixedTestFromGraph = async () => {
  if (!selectedKnowledgePoint.value) return
  // 固定：5题、仅单选
  testCounts.value.singleChoiceCount = 5
  testCounts.value.multiChoiceCount = 0
  testCounts.value.judgeCount = 0
  testCounts.value.fillCount = 0

  if (!Array.isArray(testForm.value.selectedPoints)) testForm.value.selectedPoints = []
  testForm.value.selectedPoints = [String(selectedKnowledgePoint.value).trim()]

  currentPage.value = 'exercise'
  try {
    // effectivePage 优先取路由 params.page；这里必须切路由，否则界面仍停留在 /student/graph
    await router.push('/student/exercise')
  } catch {
    /* ignore */
  }
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

const filteredLearningRecords = computed(() => {
  // 学习画像统计：直接使用所有课程数据（不再受“统计课程”筛选影响）
  return learningRecords.value || []
})

const filteredWrongBook = computed(() => {
  // 学习画像统计：直接使用所有课程数据（不再受“统计课程”筛选影响）
  return wrongBook.value || []
})

const filteredWrongBookForLearningPage = computed(() => {
  // 错题本（错题与记录页）：统一按所有课程汇总
  return wrongBook.value || []
})
const filteredLearningRecordsForLearningPage = computed(() => {
  // 学习记录（错题与记录页）：统一按所有课程汇总
  return learningRecords.value || []
})

/** 知识图谱掌握度：按当前「进入课程」的学习上下文统计，与个人中心统计课程无关 */
const learningRecordsForGraph = computed(() => {
  const c = learningContextCourse.value
  if (!c) return []
  return learningRecords.value.filter((item) => item.course === c)
})

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
  const labels = collectDescendantLabelsFromGraph(
    graphData.value.nodes,
    graphData.value.edges,
    startId
  )
  let score = 0
  let full = 0
  let attemptCount = 0
  for (const item of learningRecordsForGraph.value) {
    const anchor = String(item.practiceAnchorLabel || item.knowledgePoint || '').trim()
    if (anchor && labels.has(anchor)) {
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

const graphNetworkData = computed(() => buildGraphNetworkData(graphData.value))

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
  return `${learningContextCourse.value} ${knowledgePoint}`.trim()
}

const { testTotalCount, resetTestState, generateTest, submitTest } = useStudentGeneratedTest({
  graphData,
  questionForm,
  testForm,
  testCounts,
  testQuestions,
  testAnswers,
  testLoading,
  testError,
  testSubmitted,
  testResult,
  learningContextCourse,
  selectedKnowledgePoint,
  learningRecords,
  wrongBook,
  selectedMajorDisplay,
  selectedMajor,
  composeTopic,
  schedulePersistStudentState
})

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
  selectedCourse.value = course
  currentPage.value = 'graph'
  await router.push({ path: '/student/graph', query: { course } })
  await persistStudentState(false)
  await nextTick()
  await loadGraph()
}

const enterPaperFromGraph = async () => {
  const course = String(learningContextCourse.value || '').trim()
  const kp = String(selectedKnowledgePoint.value || '').trim()
  if (!course || !joinedCourses.value.includes(course)) return
  // 组卷页需要学习上下文课程；知识点则通过 query 传递给组卷页做默认选择
  previewUnjoinedCourse.value = ''
  learningContextCourse.value = course
  selectedCourse.value = course
  currentPage.value = 'paper'
  await router.push({ path: '/student/paper', query: { course, kp } })
  await persistStudentState(false)
}

/** 未加入：仅浏览图谱，不可点击知识点查看资料与生成建议 */
const viewCourseWithoutJoin = async (courseName) => {
  const course = String(courseName || '').trim()
  if (!course || joinedCourses.value.includes(course)) return
  learningContextCourse.value = ''
  previewUnjoinedCourse.value = course
  currentPage.value = 'graph'
  await router.push({ path: '/student/graph', query: { course } })
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

const loadResourcesByKnowledgePoint = async (pointName) => {
  const cn = learningContextCourse.value
  const kp = pointName || selectedKnowledgePoint.value || ''
  if (!cn || !kp || !props.currentUser?.id) {
    resources.value = { materials: [], tests: [], totalCount: 0 }
    resourcesError.value = ''
    resourcesLoading.value = false
    completedResourceKeys.value = []
    courseProgress.value = null
    return
  }
  resourcesLoading.value = true
  resourcesError.value = ''
  try {
    const { data } = await fetchResourcesByKnowledgePoint({
      courseName: cn,
      knowledgePoint: kp,
      includeAncestors: true,
      userId: props.currentUser.id
    })
    resources.value = data && typeof data === 'object'
      ? {
          materials: Array.isArray(data.materials) ? data.materials : [],
          tests: Array.isArray(data.tests) ? data.tests : [],
          totalCount: Number(data.totalCount || 0)
        }
      : { materials: [], tests: [], totalCount: 0 }
  } catch (e) {
    resources.value = { materials: [], tests: [], totalCount: 0 }
    resourcesError.value = e?.response?.data?.message || '资源加载失败。'
  } finally {
    resourcesLoading.value = false
  }

  try {
    const { data } = await fetchResourceProgress(props.currentUser.id, cn)
    courseProgress.value = data || null
    completedResourceKeys.value = Array.isArray(data?.completedKeys) ? data.completedKeys : []
  } catch {
    courseProgress.value = null
    completedResourceKeys.value = []
  }
}

const resourceKeyForMaterial = (m) => (m?.id != null ? `MATERIAL:${m.id}` : '')
const isResourceCompleted = (key) => Boolean(key && (completedResourceKeys.value || []).includes(key))

const markCompletedSafe = async (resourceKey) => {
  const cn = learningContextCourse.value
  if (!props.currentUser?.id || !cn || !resourceKey) return
  try {
    await markResourceComplete({ userId: props.currentUser.id, courseName: cn, resourceKey })
    // 轻量刷新进度
    const { data } = await fetchResourceProgress(props.currentUser.id, cn)
    courseProgress.value = data || null
    completedResourceKeys.value = Array.isArray(data?.completedKeys) ? data.completedKeys : completedResourceKeys.value
  } catch {
    // ignore
  }
}

const {
  teacherKpTest,
  teacherKpTestLoading,
  teacherKpTestError,
  teacherKpTestAnswers,
  teacherKpTestSubmitted,
  teacherKpTestResult,
  teacherKpTestSubmitting,
  submitTeacherKpTest,
  enterTeacherKpTestFromGraph
} = useStudentTeacherKpTest({
  currentPage,
  effectivePage,
  canStudyCurrentCourse,
  learningContextCourse,
  selectedKnowledgePoint,
  getUserId: () => props.currentUser?.id,
  learningRecords,
  wrongBook,
  selectedMajorDisplay,
  selectedMajor,
  schedulePersistStudentState,
  markCompletedSafe,
  router
})

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

const applyCourseQueryFromRoute = async () => {
  const rawCourse = route.query.course
  if (!rawCourse) return false
  const raw = Array.isArray(rawCourse) ? rawCourse[0] : rawCourse
  const courseName = String(raw || '').trim()
  if (!courseName) return false

  // 已加入课程：直接进入学习上下文
  if (joinedCourses.value.includes(courseName)) {
    previewUnjoinedCourse.value = ''
    learningContextCourse.value = courseName
    selectedCourse.value = courseName
    currentPage.value = 'graph'
    pendingGraphPointLabel.value = ''
    await nextTick()
    await loadGraph()
    return true
  }

  // 未加入课程：仅预览图谱
  if (availableCourses.value.includes(courseName)) {
    learningContextCourse.value = ''
    previewUnjoinedCourse.value = courseName
    currentPage.value = 'graph'
    pendingGraphPointLabel.value = ''
    await nextTick()
    await loadGraph()
    return true
  }

  return false
}

const loadGraph = async () => {
  const topic = learningContextCourse.value || previewUnjoinedCourse.value
  const emptyGraph = () => {
    graphData.value = { title: '知识图谱', nodes: [], edges: [], suggestions: [] }
    selectedNodeId.value = ''
    selectedKnowledgePoint.value = ''
    resources.value = { materials: [], tests: [], totalCount: 0 }
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
        await loadResourcesByKnowledgePoint(label)
        void loadLearningSuggestionsFor(label)
        void loadMajorRelevanceFor(label)
      } else if (isPreview) {
        resources.value = { materials: [], tests: [], totalCount: 0 }
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
        void loadResourcesByKnowledgePoint(name)
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

const wrongBookModalItem = ref(null)
const openWrongBookModal = (item) => {
  wrongBookModalItem.value = item || null
}
const closeWrongBookModal = () => {
  wrongBookModalItem.value = null
}

// 公告已并入「通知」弹窗；学生端不再单独提供公告页。

watch(currentPage, (v) => {
  if (v !== 'review') closeWrongBookModal()
})

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
  [currentPage, availableCourses, joinedCourses, myCourseCatalog],
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

// 公告页已移除：无需在切页时拉取公告

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
        await loadResourcesByKnowledgePoint(kp)
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

watch(
  () => route.query.course,
  () => {
    if (!stateHydrated.value) return
    if (effectivePage.value === 'course-detail') {
      const c = Array.isArray(route.query.course) ? route.query.course[0] : route.query.course
      void loadCourseDetail(c)
      return
    }
    void applyCourseQueryFromRoute()
  }
)

onMounted(async () => {
  await loadStudentState()
  await loadMyCourseCatalog()
  const hadCourseQuery = Boolean(route.query.course)
  const isDetailPage = effectivePage.value === 'course-detail'
  if (isDetailPage && hadCourseQuery) {
    const c = Array.isArray(route.query.course) ? route.query.course[0] : route.query.course
    await loadCourseDetail(c)
  }
  const appliedCourseQuery = (!isDetailPage && hadCourseQuery) ? await applyCourseQueryFromRoute() : false
  const shouldApplyDiscussionDeepLink = !appliedCourseQuery && Boolean(route.query.dc && route.query.dp)
  if (shouldApplyDiscussionDeepLink) {
    await applyDiscussionDeepLinkFromRoute()
  } else if (canStudyCurrentCourse.value || previewUnjoinedCourse.value) {
    loadGraph()
  }
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
  <div class="student-lms-shell">
    <section class="student-lms-main">
      <div class="student-lms-content">
    <StudentHome
      v-if="effectivePage === 'home'"
      :user-initial="userInitial"
      :profile-form="profileForm"
      :current-user="currentUser"
      :selected-course="selectedCourse"
      :joined-courses="joinedCourses"
      :selected-major-display="selectedMajorDisplay"
      :learning-stats="learningStats"
      :dimension-scores="dimensionScores"
      :dimension-scores-loading="dimensionScoresLoading"
      :dimension-scores-error="dimensionScoresError"
      :filtered-wrong-book-count="filteredWrongBook.length"
      :profile-message="profileMessage"
      @update:selected-course="(v) => (selectedCourse = v)"
      @edit-profile="openEditProfile"
      @change-password="openPasswordPage"
      @logout="emit('logout')"
    />

    <!-- 修改密码已改为模态窗口 -->

    <StudentCourses
      v-if="effectivePage === 'courses'"
      :joined-courses="joinedCourses"
      :my-course-catalog="myCourseCatalog"
      :state-hydrated="stateHydrated"
      :teachers-by-course="teachersByCourse"
      :teachers-loading="teachersByCourseLoading"
      @enter="openCourseDetailFromMyCourses"
      @quit="async (c) => { await quitCourse(c); await loadMyCourseCatalog() }"
    />

    <CourseDetailPanel
      v-if="effectivePage === 'course-detail'"
      role="student"
      :detail="courseDetail || { courseName: '', coverUrl: '', summary: '', syllabus: '' }"
      :loading="courseDetailLoading"
      :error="courseDetailError"
      :teachers-text="courseDetail?.courseName ? formatTeachersForCourse(courseDetail.courseName) : ''"
      :can-access="Boolean(courseDetail?.hasAccess)"
      :can-edit-meta="false"
      :is-submitting="false"
      :edit-form="{ coverUrl: '', summary: '', syllabus: '' }"
      @join="async () => { if (courseDetail?.courseName) { await joinCourse(courseDetail.courseName); await loadMyCourseCatalog(); await enterCourseFromMarket(courseDetail.courseName) } }"
      @enter="async () => { if (courseDetail?.courseName) await enterCourseFromMarket(courseDetail.courseName) }"
      @apply="() => null"
      @save-meta="() => null"
      @update:edit-form="() => null"
    />

    <section v-if="effectivePage === 'graph'" class="panel-stack">
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
        :materials="[]"
        :practice-test-allowed="practiceTestAllowed"
        @go-courses="currentPage = 'courses'"
        @refresh-graph="loadGraph"
        @enter-test="enterFixedTestFromGraph"
      >
        <article class="result-card">
          <h3 class="panel-title">{{ graphData.title }}</h3>
          <div class="inline-form">
            <button type="button" class="match-button" :disabled="graphLoading" @click="loadGraph">
              {{ graphLoading ? '加载中...' : '刷新图谱' }}
            </button>
          </div>
          <p v-if="graphError" class="error-text">{{ graphError }}</p>
          <div ref="graphChartRef" style="width: 100%; height: 420px;"></div>
        </article>

        <StudentGraphLearningPanel
          :selected-node="selectedNode"
          :is-unjoined-preview-mode="isUnjoinedPreviewMode"
          :graph-node-mastery="graphNodeMastery"
          :learning-suggestions="learningSuggestions"
          :suggestion-loading="suggestionLoading"
          :suggestion-error="suggestionError"
          :major-relevance="majorRelevance"
          :relevance-loading="relevanceLoading"
          :relevance-error="relevanceError"
          :relevance-label="relevanceLabel"
          :course-progress="courseProgress"
          :resources-loading="resourcesLoading"
          :resources-error="resourcesError"
          :resources="resources"
          :learning-context-course="learningContextCourse"
          :selected-knowledge-point="selectedKnowledgePoint"
          :current-user="currentUser"
          :discussion-focus-post-id="discussionFocusPostId"
          :practice-test-allowed="practiceTestAllowed"
          :enter-fixed-test-from-graph="enterFixedTestFromGraph"
          :enter-paper-from-graph="enterPaperFromGraph"
          :enter-teacher-kp-test-from-graph="enterTeacherKpTestFromGraph"
          :is-resource-completed="isResourceCompleted"
          :resource-key-for-material="resourceKeyForMaterial"
          :mark-completed-safe="markCompletedSafe"
        />
      </StudentGraph>
    </section>

    <StudentPaperComposer
      v-if="effectivePage === 'paper'"
      :joined-courses="joinedCourses"
      :current-course="learningContextCourse || selectedCourse"
      :render-latex-text="renderLatexText"
      :refresh-saved-exams="loadSavedExams"
      @go-courses="currentPage = 'courses'"
    />

    <StudentExercise
      v-if="effectivePage === 'exercise'"
      :can-study-current-course="canStudyCurrentCourse"
      :selected-knowledge-point="selectedKnowledgePoint"
      :question-form="questionForm"
      :test-loading="testLoading"
      :test-error="testError"
      :test-questions="testQuestions"
      :test-submitted="testSubmitted"
      :test-result="testResult"
      :test-answers="testAnswers"
      :generate-test="generateTest"
      :submit-test="submitTest"
      :render-latex-text="renderLatexText"
      :parse-option-letter="parseOptionLetter"
      :parse-option-text="parseOptionText"
      :resolve-answer-text="resolveAnswerText"
      @go-courses="currentPage = 'courses'"
    />

    <StudentTeacherTest
      v-if="effectivePage === 'teacher-test'"
      v-model:teacher-kp-test-answers="teacherKpTestAnswers"
      :can-study-current-course="canStudyCurrentCourse"
      :selected-knowledge-point="selectedKnowledgePoint"
      :teacher-kp-test="teacherKpTest"
      :teacher-kp-test-loading="teacherKpTestLoading"
      :teacher-kp-test-error="teacherKpTestError"
      :teacher-kp-test-submitted="teacherKpTestSubmitted"
      :teacher-kp-test-result="teacherKpTestResult"
      :teacher-kp-test-submitting="teacherKpTestSubmitting"
      :submit-teacher-kp-test="submitTeacherKpTest"
      :render-latex-text="renderLatexText"
      :parse-option-letter="parseOptionLetter"
      :parse-option-text="parseOptionText"
      :resolve-answer-text="resolveAnswerText"
      @go-courses="currentPage = 'courses'"
    />

    <StudentReview
      v-if="effectivePage === 'review'"
      :filtered-wrong-book-for-learning-page="filteredWrongBookForLearningPage"
      :filtered-learning-records-for-learning-page="filteredLearningRecordsForLearningPage"
      :saved-exams="savedExams"
      :exam-error="examError"
      :wrong-book-modal-item="wrongBookModalItem"
      :wrong-drill-course="wrongDrillCourse"
      :wrong-drill-course-options="wrongDrillCourseOptions"
      :wrong-drill-session="wrongDrillSession"
      :wrong-drill-error="wrongDrillError"
      :wrong-drill-submitting="wrongDrillSubmitting"
      :infer-wrong-book-question-type="inferWrongBookQuestionType"
      :set-wrong-drill-course="setWrongDrillCourse"
      :start-wrong-drill="startWrongDrill"
      :cancel-wrong-drill="cancelWrongDrill"
      :submit-wrong-drill="submitWrongDrill"
      :render-latex-text="renderLatexText"
      :parse-option-letter="parseOptionLetter"
      :parse-option-text="parseOptionText"
      :wrong-book-question-preview="wrongBookQuestionPreview"
      :open-wrong-book-modal="openWrongBookModal"
      :close-wrong-book-modal="closeWrongBookModal"
      :confirm-delete-exam="confirmDeleteExam"
      :download-exam="downloadExam"
      :render-exam-pdfs="renderExamPdfs"
      @go-courses="currentPage = 'courses'"
    />

    <!-- 兜底：避免 unknown page 导致整页空白 -->
    <article
      v-if="!['home','courses','course-detail','graph','paper','exercise','teacher-test','review'].includes(effectivePage)"
      class="result-card"
    >
      <h3>页面不存在</h3>
      <p class="panel-subtitle">未识别的页面：{{ String(route.params.page || props.activePage || '') }}</p>
      <button type="button" class="nav-btn" @click="() => router.push('/student/home')">返回个人中心</button>
    </article>

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
      </div>
    </section>
  </div>
    <AiAssistantWidget role="student" :current-user="currentUser" />
</template>

<style src="./student-portal.css"></style>
