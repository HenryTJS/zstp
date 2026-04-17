<script setup>
import {
  StudentGraph,
  StudentGraphLearningPanel,
  StudentGraphCanvasCard,
  useStudentGraphPage
} from './graph'
import { StudentExercise, StudentTeacherTest, StudentPaperComposer, useStudentGeneratedTest, useStudentTeacherKpTest, useStudentLearningModule } from './learning'
import { StudentReview, useStudentWrongDrill, useStudentReviewModule } from './review'
import { StudentCourses, CourseDetailPanel, useStudentCourseModule } from './course'
import { StudentHome, StudentEditProfileModal, StudentChangePasswordModal, useStudentPersistState, useStudentProfileModule } from './profile'
import { useStudentPortalOrchestration } from './composables/useStudentPortalOrchestration'
import { buildGraphNetworkData } from './utils/studentGraphNetwork'
import { collectDescendantLabelsFromGraph } from './utils/studentGraphTraversal'
import { renderLatexText } from '../shared/utils/renderLatexHtml'
import { wrongBookQuestionPreview } from './utils/studentWrongBookUi'
import {
  parseOptionLetter,
  parseOptionText,
  resolveAnswerText,
  unescapeNewlinesSafe,
  wrongBookChoiceLetters
} from './utils/studentTestAnswerUtils'

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
const shell = inject(appShellKey, null)
const relayLogout = () => {
  if (typeof shell?.logout === 'function') void shell.logout()
  else emit('logout')
}
const relayUpdateUser = (patch) => {
  if (typeof shell?.updateUser === 'function') shell.updateUser(patch)
  else emit('update-user', patch)
}

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

/** 与顶栏一致：用 path 首段判断当前页，避免 /student/:page? 下 params 偶发为空 */
const studentPathSegment = computed(() => {
  const p = route.path
  if (!p.startsWith('/student')) return 'home'
  let rest = p.slice('/student'.length)
  if (rest.startsWith('/')) rest = rest.slice(1)
  const seg = (rest.split('/')[0] || '').trim()
  return seg || 'home'
})

const effectivePage = computed(() =>
  normalizeStudentPage(studentPathSegment.value || props.activePage || currentPage.value || 'home')
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

// 以 path 为准同步 currentPage，与 App.vue 顶栏高亮一致
watch(
  () => studentPathSegment.value,
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
const totalLearningSeconds = ref(0)
/** 从服务器恢复学生状态期间：不因 availableCourses 尚未就绪而裁剪 joinedCourses */
const hydrateJoiningCourses = ref(false)
const dimensionScores = ref(null)
const dimensionScoresLoading = ref(false)
const dimensionScoresError = ref('')
const discussionPostCount = ref(0)
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
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AiAssistantWidget from '../shared/components/AiAssistantWidget.vue'
import {
  countKnowledgePointDiscussionsByUser,
  fetchKnowledgeGraph,
  fetchResourcesByKnowledgePoint,
  fetchResourceProgress,
  markResourceComplete,
  listKnowledgePoints,
  fetchLearningSuggestions,
  fetchMajorRelevance,
  saveExam,
  listCoursesByMajor
} from '../api/client'
const resources = ref({ materials: [], tests: [], totalCount: 0 })
const resourcesLoading = ref(false)
const resourcesError = ref('')
const completedResourceKeys = ref([])
const courseProgress = ref(null) // { total, completed, percent }
const selectedKnowledgePoint = ref('')

import { http } from '../api/client'
import { appShellKey } from '../appShell'
const joinedCourses = ref([])

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
  loadDimensionScores,
  clearPendingTimers
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

// isUnjoinedPreviewMode is provided by useStudentGraphPage
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

const onGraphKnowledgePointSelected = async (label) => {
  const name = String(label || '').trim()
  if (!name) return
  await loadResourcesByKnowledgePoint(name)
  void loadLearningSuggestionsFor(name)
  void loadMajorRelevanceFor(name)
}

const {
  graphLoading,
  graphError,
  graphData,
  selectedNodeId,
  graphClickedNodeCategory,
  graphView,
  isUnjoinedPreviewMode,
  loadGraph,
  selectKnowledgePointFromGraph
} = useStudentGraphPage({
  learningContextCourseRef: learningContextCourse,
  previewUnjoinedCourseRef: previewUnjoinedCourse,
  pendingGraphPointLabelRef: pendingGraphPointLabel,
  selectedKnowledgePointRef: selectedKnowledgePoint,
  fetchKnowledgeGraph,
  onKnowledgePointSelected: onGraphKnowledgePointSelected
})

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

const {
  examError,
  savedExams,
  wrongBookModalItem,
  loadSavedExams,
  openWrongBookModal,
  closeWrongBookModal,
  downloadExam,
  renderExamPdfs,
  confirmDeleteExam
} = useStudentReviewModule()

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
  const scopedCourse = String(route.query.course || '').trim()
  const all = wrongBook.value || []
  if (!scopedCourse) return all
  return all.filter((item) => String(item?.course || '').trim() === scopedCourse)
})
const filteredLearningRecordsForLearningPage = computed(() => {
  const scopedCourse = String(route.query.course || '').trim()
  const all = learningRecords.value || []
  if (!scopedCourse) return all
  return all.filter((item) => String(item?.course || '').trim() === scopedCourse)
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

const formatSecondsToDuration = (seconds) => {
  const s = Math.max(0, Number(seconds || 0))
  if (!Number.isFinite(s) || s <= 0) return '0分钟'
  const totalMinutes = Math.floor(s / 60)
  const h = Math.floor(totalMinutes / 60)
  const rest = totalMinutes % 60
  if (h <= 0) return `${rest}分钟`
  if (rest === 0) return `${h}小时`
  return `${h}小时${rest}分钟`
}

const learningTimerStartedAtMs = ref(0)
const learningTimerDisplayNowMs = ref(Date.now())
let learningTimerInterval = null

const isLearningTimerPage = computed(() => effectivePage.value !== 'home')
const liveLearningSeconds = computed(() => {
  const base = Math.max(0, Number(totalLearningSeconds.value || 0))
  if (!learningTimerStartedAtMs.value) return base
  const deltaSec = Math.max(0, Math.floor((learningTimerDisplayNowMs.value - learningTimerStartedAtMs.value) / 1000))
  return base + deltaSec
})

const startLearningTimer = () => {
  if (learningTimerStartedAtMs.value) return
  learningTimerStartedAtMs.value = Date.now()
  learningTimerDisplayNowMs.value = Date.now()
  if (!learningTimerInterval) {
    learningTimerInterval = window.setInterval(() => {
      learningTimerDisplayNowMs.value = Date.now()
    }, 1000)
  }
}

const stopLearningTimerAndAccumulate = (persist = true) => {
  if (!learningTimerStartedAtMs.value) return
  const elapsedSec = Math.max(0, Math.floor((Date.now() - learningTimerStartedAtMs.value) / 1000))
  learningTimerStartedAtMs.value = 0
  learningTimerDisplayNowMs.value = Date.now()
  if (elapsedSec > 0) {
    totalLearningSeconds.value = Math.max(0, Number(totalLearningSeconds.value || 0)) + elapsedSec
    if (persist) schedulePersistStudentState()
  }
  if (learningTimerInterval) {
    window.clearInterval(learningTimerInterval)
    learningTimerInterval = null
  }
}

const learningStats = computed(() => {
  const records = filteredLearningRecords.value || []
  return {
    joinedCoursesCount: (joinedCourses.value || []).length,
    totalLearningDurationText: formatSecondsToDuration(liveLearningSeconds.value),
    publishedCommentCount: Number(discussionPostCount.value || 0),
    hasTestRecord: records.length > 0
  }
})

const loadDiscussionPostCount = async () => {
  const uid = props.currentUser?.id
  if (!uid) {
    discussionPostCount.value = 0
    return
  }
  try {
    const { data } = await countKnowledgePointDiscussionsByUser(uid)
    discussionPostCount.value = Number(data?.count || 0)
  } catch {
    discussionPostCount.value = 0
  }
}

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

const {
  enterPaperFromGraph,
  clearExerciseUiAfterQuittingCurrentCourse
} = useStudentLearningModule({
  learningContextCourseRef: learningContextCourse,
  selectedKnowledgePointRef: selectedKnowledgePoint,
  joinedCoursesRef: joinedCourses,
  previewUnjoinedCourseRef: previewUnjoinedCourse,
  selectedCourseRef: selectedCourse,
  currentPageRef: currentPage,
  router,
  persistStudentState,
  generatedQuestionRef: generatedQuestion,
  questionErrorRef: questionError,
  questionLoadingRef: questionLoading,
  practiceResultRef: practiceResult,
  practiceErrorRef: practiceError,
  practiceAnswerRef: practiceAnswer,
  selectedChoiceAnswerRef: selectedChoiceAnswer,
  answerImageFileRef: answerImageFile,
  answerImageBase64Ref: answerImageBase64,
  resetTestState,
  testLoadingRef: testLoading,
  examErrorRef: examError,
  testFormRef: testForm
})

const enterReviewFromGraph = async () => {
  const cn = String(learningContextCourse.value || '').trim()
  currentPage.value = 'review'
  if (cn && typeof setWrongDrillCourse === 'function') {
    setWrongDrillCourse(cn)
  }
  try {
    await router.push({ path: '/student/review', query: cn ? { course: cn } : {} })
  } catch {
    /* ignore */
  }
}

const {
  myCourseCatalog,
  courseDetail,
  courseDetailLoading,
  courseDetailError,
  courseProgressByCourse,
  marketCourseProgressLoading,
  marketCourseNamesForProgress,
  teachersByCourse,
  teachersByCourseLoading,
  loadMarketCourseProgress,
  loadTeachersForMarketCourses,
  refreshCoursePageData,
  formatTeachersForCourse,
  loadMyCourseCatalog,
  loadCourseDetail,
  clearCourseDetail,
  syncCourseDetailFromQuery,
  openCourseDetailFromMyCourses,
  joinCourse,
  enterCourseFromMarket,
  quitCourse
} = useStudentCourseModule({
  currentUserRef: computed(() => props.currentUser),
  selectedMajorRef: selectedMajor,
  joinedCoursesRef: joinedCourses,
  selectedCourseRef: selectedCourse,
  learningContextCourseRef: learningContextCourse,
  previewUnjoinedCourseRef: previewUnjoinedCourse,
  currentPageRef: currentPage,
  router,
  persistStudentState,
  loadGraph,
  learningRecordsRef: learningRecords,
  wrongBookRef: wrongBook,
  wrongBookModalItemRef: wrongBookModalItem,
  profileMessageRef: profileMessage,
  clearExerciseUiAfterQuittingCurrentCourse
})

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
    if (data && typeof data === 'object') {
      courseProgressByCourse.value = {
        ...courseProgressByCourse.value,
        [cn]: {
          percent: Number(data.percent ?? 0),
          completed: Number(data.completed ?? 0),
          total: Number(data.total ?? 0)
        }
      }
    }
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
    if (data && typeof data === 'object') {
      courseProgressByCourse.value = {
        ...courseProgressByCourse.value,
        [cn]: {
          percent: Number(data.percent ?? 0),
          completed: Number(data.completed ?? 0),
          total: Number(data.total ?? 0)
        }
      }
    }
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

// selectKnowledgePointFromGraph is provided by useStudentGraphPage


// 公告已并入「通知」弹窗；学生端不再单独提供公告页。

const {
  editProfileVisible,
  editProfileForm,
  changePasswordVisible,
  openEditProfile,
  handleSaveProfile,
  openPasswordPage
} = useStudentProfileModule({
  currentUserRef: computed(() => props.currentUser),
  profileFormRef: profileForm,
  profileMessageRef: profileMessage,
  selectedMajor1Ref: selectedMajor1,
  selectedMajor2Ref: selectedMajor2,
  selectedMajor3Ref: selectedMajor3,
  majorLevel2Ref: majorLevel2,
  majorLevel3Ref: majorLevel3,
  loadMajorLevel2,
  loadMajorLevel3,
  persistStudentState,
  relayUpdateUser
})

ensureCourseSelection()

useStudentPortalOrchestration({
  route,
  router,
  stateHydrated,
  currentPage,
  effectivePage,
  learningContextCourse,
  kpCascade1,
  kpCascade2,
  kpCascade3,
  loadExamPoints,
  schedulePersistStudentState,
  loadGraph,
  graphData,
  selectedKnowledgePoint,
  previewUnjoinedCourse,
  canShowGraphPage,
  loadResourcesByKnowledgePoint,
  loadLearningSuggestionsFor,
  loadMajorRelevanceFor,
  closeWrongBookModal,
  loadSavedExams,
  joinedCourses,
  availableCourses,
  myCourseCatalog,
  pendingGraphPointLabel,
  discussionFocusPostId,
  selectedCourse,
  refreshCoursePageData,
  marketCourseNamesForProgress,
  getCurrentUserId: () => props.currentUser?.id,
  selectedMajor1,
  selectedMajor2,
  selectedMajor3,
  syncCourseDetailFromQuery,
  clearCourseDetail,
  canStudyCurrentCourse,
  loadStudentState,
  loadMyCourseCatalog
})

onBeforeUnmount(() => {
  stopLearningTimerAndAccumulate(false)
  if (learningTimerInterval) {
    window.clearInterval(learningTimerInterval)
    learningTimerInterval = null
  }
  clearPendingTimers()
})

watch(
  () => props.currentUser?.id,
  () => {
    void loadDiscussionPostCount()
  },
  { immediate: true }
)

watch(
  () => [effectivePage.value, stateHydrated.value],
  () => {
    if (!stateHydrated.value) return
    if (isLearningTimerPage.value) startLearningTimer()
    else stopLearningTimerAndAccumulate(true)
  },
  { immediate: true }
)

const handleCourseDetailJoin = async () => {
  const cn = String(courseDetail.value?.courseName || '').trim()
  if (!cn) return
  await joinCourse(cn)
  await loadMyCourseCatalog()
  await enterCourseFromMarket(cn)
}

const handleCourseDetailEnter = async () => {
  const cn = String(courseDetail.value?.courseName || '').trim()
  if (!cn) return
  await enterCourseFromMarket(cn)
}

const handleCourseDetailQuit = async () => {
  const cn = String(courseDetail.value?.courseName || '').trim()
  if (!cn) return
  await quitCourse(cn)
  await loadMyCourseCatalog()
  currentPage.value = 'courses'
  await router.push({ path: '/student/courses' })
}

</script>

<template>
  <div class="student-lms-shell">
    <section class="student-lms-main">
      <div class="student-lms-content" :class="{ 'student-page-flat': effectivePage !== 'home' }">
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
      @logout="relayLogout"
    />

    <!-- 修改密码已改为模态窗口 -->

    <StudentCourses
      v-if="effectivePage === 'courses'"
      :joined-courses="joinedCourses"
      :my-course-catalog="myCourseCatalog"
      :state-hydrated="stateHydrated"
      :teachers-by-course="teachersByCourse"
      :teachers-loading="teachersByCourseLoading"
      :course-progress-by-course="courseProgressByCourse"
      :course-progress-loading="marketCourseProgressLoading"
      @open-detail="openCourseDetailFromMyCourses"
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
      @join="handleCourseDetailJoin"
      @enter="handleCourseDetailEnter"
      @quit="handleCourseDetailQuit"
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
        <StudentGraphCanvasCard
          :view="graphView"
          :graph-loading="graphLoading"
          :graph-error="graphError"
          :graph-data="graphData"
          :graph-network-data="graphNetworkData"
          :is-unjoined-preview-mode="isUnjoinedPreviewMode"
          @update:view="(v) => (graphView = v)"
          @refresh="loadGraph"
          @node-click="({ id, label, category }) => selectKnowledgePointFromGraph({ id, label, category })"
        />

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
          :enter-review-from-graph="enterReviewFromGraph"
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
      :saved-exams="savedExams"
      :exam-error="examError"
      :confirm-delete-exam="confirmDeleteExam"
      :render-exam-pdfs="renderExamPdfs"
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
      @go-courses="currentPage = 'courses'"
    />

    <!-- 兜底：避免 unknown page 导致整页空白 -->
    <article
      v-if="!['home','courses','course-detail','graph','paper','exercise','teacher-test','review'].includes(effectivePage)"
      class="result-card"
    >
      <h3 class="portal-section-title portal-section-title--slate">页面不存在</h3>
      <p class="panel-subtitle">未识别的页面：{{ String(studentPathSegment || props.activePage || '') }}</p>
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

<style>
@import '@/student/styles/student-portal.css';

.student-page-flat > .result-card,
.student-page-flat > .panel-stack > .result-card {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 !important;
}
</style>
