<script setup>
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
import AccountSecurityPanel from './AccountSecurityPanel.vue'
import { fetchGrading, fetchKnowledgeGraph, fetchQuestion, fetchQuestions, fetchStudentState, saveStudentState, fetchMaterialsByKnowledgePoint, updateUser, listKnowledgePoints, fetchExam, fetchExams, deleteExam, fetchLearningSuggestions, fetchMajorRelevance, saveExam, fetchAnnouncements } from '../api/client'
const materials = ref([])
const selectedKnowledgePoint = ref('')

import { http } from '../api/client'
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
      // 保存学生端学习状态（包含专业/课程选择）
      await persistStudentState(true)
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
import { listCoursesByMajor } from '../api/client'
const availableCourses = ref([])
watch([selectedMajor1, selectedMajor2, selectedMajor3], async () => {
  // 优先三级
  let code = selectedMajor3.value || selectedMajor2.value || selectedMajor1.value
  if (!code) { availableCourses.value = []; return }
  try {
    const { data } = await listCoursesByMajor(code)
    availableCourses.value = Array.isArray(data) ? data : []
  } catch {
    availableCourses.value = []
  }
  // 自动切换当前课程
  if (!availableCourses.value.includes(selectedCourse.value)) {
    selectedCourse.value = availableCourses.value[0] || ''
  }
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
const selectedCourse = ref('高等数学')

const graphLoading = ref(false)
const graphError = ref('')
const graphData = ref({ title: '', nodes: [], edges: [], suggestions: [] })
const selectedNodeId = ref('')
const graphChartRef = ref(null)
let graphChart = null

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

// 批量组卷表单
const examForm = ref({
  selectedPoints: [],
  singleChoiceCount: 0,
  multiChoiceCount: 0,
  judgeCount: 0,
  fillCount: 0,
  shortCount: 0,
  essayCount: 0,
  title: ''
})
const examLoading = ref(false)
const examResult = ref(null)
const examError = ref('')

// 出题模式：single = 单题，batch = 组卷
const examMode = ref('single')

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
    const { data } = await listKnowledgePoints(selectedCourse.value)
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

watch(selectedCourse, () => {
  kpCascade1.value = ''
  kpCascade2.value = ''
  kpCascade3.value = ''
  loadExamPoints()
}, { immediate: true })

const sortPoints = (a, b) => {
  const oa = Number(a?.sortOrder ?? 0)
  const ob = Number(b?.sortOrder ?? 0)
  if (oa !== ob) return oa - ob
  return String(a?.pointName || '').localeCompare(String(b?.pointName || ''), 'zh-CN')
}

/** 一级：父节点为课程名，或旧数据无父节点且名称不等于课程名（课程名单独作为 0 级） */
const kpLevel1Options = computed(() => {
  const cn = selectedCourse.value
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
  kpCascade3.value || kpCascade2.value || kpCascade1.value || selectedCourse.value || ''

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

// 切换“单题/组卷”时重置对应预览状态，避免串台
watch(examMode, (mode) => {
  if (mode === 'batch') {
    generatedQuestion.value = null
    questionError.value = ''
    questionLoading.value = false
    practiceResult.value = null
    practiceError.value = ''
    practiceAnswer.value = ''
    selectedChoiceAnswer.value = ''
    answerImageFile.value = null
    answerImageBase64.value = ''

    // 测试模式状态清空
    testQuestions.value = []
    testAnswers.value = []
    testSubmitted.value = false
    testResult.value = null
    testError.value = ''
    testLoading.value = false
  } else {
    examResult.value = null
    examError.value = ''
    examLoading.value = false

    // 组卷模式状态清空（保留测试输入）
    savedExams.value = savedExams.value || []
  }
})

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

const normalizeExamQuestionType = (label) => {
  // UI: 单选/多选/判断/填空/简答/解答
  // 后端 AiService 支持：选择题/多选题/判断题/填空题/解答题（简答题会被归一化为解答题）
  const s = String(label || '').trim()
  if (s === '单选') return '选择题'
  if (s === '多选') return '多选题'
  if (s === '判断') return '判断题'
  if (s === '填空') return '填空题'
  if (s === '简答') return '简答题'
  if (s === '解答') return '解答题'
  return '解答题'
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
      const topic = composeTopic(kp) + `（题${idx + 1}）`
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
        course: selectedCourse.value || '',
        knowledgePoint: kp || '未标注',
        score,
        fullScore: full
      })

      if (score < full) {
        newWrongItems.push({
          id: `wb-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 7)}`,
          major: selectedMajorDisplay.value || selectedMajor.value || '',
          course: selectedCourse.value || '',
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
  for (const item of filteredLearningRecords.value) {
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
  return `${selectedMajor.value} ${selectedCourse.value} ${knowledgePoint}`.trim()
}

const ensureCourseSelection = () => {
  if (!availableCourses.value.includes(selectedCourse.value)) {
    selectedCourse.value = availableCourses.value[0] || ''
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

const generateExam = async () => {
  examError.value = ''
  examResult.value = null
  const total =
    Number(examForm.value.singleChoiceCount || 0) +
    Number(examForm.value.multiChoiceCount || 0) +
    Number(examForm.value.judgeCount || 0) +
    Number(examForm.value.fillCount || 0) +
    Number(examForm.value.shortCount || 0) +
    Number(examForm.value.essayCount || 0)
  if (total < 1 || total > 10) {
    examError.value = '请确保总题数在 1 到 10 之间。'
    return
  }
  if (!Array.isArray(examForm.value.selectedPoints) || !examForm.value.selectedPoints.length) {
    examError.value = '请至少选择一个知识点。'
    return
  }
  examLoading.value = true
  try {
    // 组卷与测试一致：按 2 题一批并发请求，最多 5 次请求（=> 最多 10 题）
    const typeList = []
    for (let i = 0; i < Number(examForm.value.singleChoiceCount || 0); i++) typeList.push('单选')
    for (let i = 0; i < Number(examForm.value.multiChoiceCount || 0); i++) typeList.push('多选')
    for (let i = 0; i < Number(examForm.value.judgeCount || 0); i++) typeList.push('判断')
    for (let i = 0; i < Number(examForm.value.fillCount || 0); i++) typeList.push('填空')
    for (let i = 0; i < Number(examForm.value.shortCount || 0); i++) typeList.push('简答')
    for (let i = 0; i < Number(examForm.value.essayCount || 0); i++) typeList.push('解答')

    const topics = Array.isArray(examForm.value.selectedPoints) ? examForm.value.selectedPoints : []
    const diff = questionForm.value.difficulty || '中等'
    const major = selectedMajor.value

    const specs = typeList.map((t, idx) => {
      const kp = topics[idx % topics.length]
      const topic = composeTopic(kp) + `（卷${idx + 1}）`
      return { topic, difficulty: diff, questionType: normalizeExamQuestionType(t), major }
    })

    const chunk2 = (arr) => {
      const out = []
      for (let i = 0; i < arr.length; i += 2) out.push(arr.slice(i, i + 2))
      return out
    }

    const batches = chunk2(specs).slice(0, 5)
    if (batches.length > 5) {
      examError.value = '生成请求次数超限（最多 5 次），请减少题目数量。'
      return
    }

    const responses = await Promise.all(batches.map((items) => fetchQuestions({ items })))
    const results = []
    for (const resp of responses) {
      const data = resp && resp.data ? resp.data : resp
      const qs = Array.isArray(data?.questions) ? data.questions : []
      for (const q of qs) results.push(q)
    }

    const trimmed = results.slice(0, 10)
    examResult.value = (trimmed || []).map((q) => {
      if (!q) return q
      return {
        ...q,
        question: unescapeNewlinesSafe(q.question),
        explanation: unescapeNewlinesSafe(q.explanation),
        answer: unescapeNewlinesSafe(q.answer),
        options: Array.isArray(q.options) ? q.options.map(unescapeNewlinesSafe) : [],
        knowledge_points: Array.isArray(q.knowledge_points) ? q.knowledge_points.map(unescapeNewlinesSafe) : []
      }
    })
  } catch (err) {
    examError.value = err?.response?.data?.message || '题卷生成失败。'
  } finally {
    examLoading.value = false
  }
}

const persistGeneratedExam = async () => {
  examError.value = ''
  if (!Array.isArray(examResult.value) || !examResult.value.length) {
    examError.value = '请先生成试卷题目。'
    return
  }
  try {
    const payload = {
      title: examForm.value.title || (selectedCourse.value + ' 试卷'),
      questions: examResult.value
    }
    const resp = await saveExam(payload)
    const data = resp && resp.data ? resp.data : resp
    await loadSavedExams()
    if (data?.examId) {
      // 保存成功后直接下载（若后端已生成 Markdown）
      if (data.mdOriginalPresent) {
        try { downloadExam(data.examId, 'md_paper') } catch (e) {}
      }
      if (data.mdAnswerPresent) {
        try { downloadExam(data.examId, 'md_answer') } catch (e) {}
      }
    }
  } catch (err) {
    examError.value = err?.response?.data?.message || '保存试卷失败，请稍后重试。'
  }
}

const addExamPoint = () => {
  const p = pickCascadePoint()
  if (!p) return
  if (!Array.isArray(examForm.value.selectedPoints)) examForm.value.selectedPoints = []
  if (!examForm.value.selectedPoints.includes(p)) {
    examForm.value.selectedPoints.push(p)
  }
  clearCascadeAfterAdd()
}
const removeExamPoint = (p) => {
  if (!Array.isArray(examForm.value.selectedPoints)) return
  examForm.value.selectedPoints = examForm.value.selectedPoints.filter((x) => x !== p)
}

// 三级联动后不再需要 normalizeMajor

const loadStudentState = async () => {
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
    ensureCourseSelection()

    if (availableCourses.value.includes(data.courseName)) {
      selectedCourse.value = data.courseName
    }

    // removed handling of profile bio (学习目标) per UI change

    learningRecords.value = Array.isArray(data.learningRecords) ? data.learningRecords : []
    wrongBook.value = Array.isArray(data.wrongBook) ? data.wrongBook : []
  } catch {
    profileMessage.value = '未读取到历史学习状态，已使用默认配置。'
  } finally {
    stateHydrated.value = true
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
      courseName: selectedCourse.value,
      learningRecords: learningRecords.value,
      wrongBook: wrongBook.value
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
  if (!selectedCourse.value || !kp) {
    learningSuggestions.value = []
    suggestionLoading.value = false
    return
  }

  try {
    const resp = await fetchLearningSuggestions({
      topic: selectedCourse.value,
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
  if (!selectedCourse.value || !kp || !majorText) {
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
      topic: selectedCourse.value,
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
  if (!selectedCourse.value || !pointName) {
    materials.value = []
    return
  }
  try {
    const resp = await fetchMaterialsByKnowledgePoint(selectedCourse.value, pointName, false)
    materials.value = resp.data
  } catch {
    materials.value = []
  }
}

const loadGraph = async () => {
  graphLoading.value = true
  graphError.value = ''
  try {
    const { data } = await fetchKnowledgeGraph({ topic: selectedCourse.value })
    graphData.value = {
      title: data.title || '知识图谱',
      nodes: Array.isArray(data.nodes) ? data.nodes : [],
      edges: Array.isArray(data.edges) ? data.edges : [],
      suggestions: Array.isArray(data.suggestions) ? data.suggestions : []
    }
    // 作为兜底先展示 knowledge-graph 返回的建议，随后再为“当前知识点”异步生成新建议
    learningSuggestions.value = Array.isArray(graphData.value.suggestions) ? graphData.value.suggestions : []

    selectedNodeId.value = 'root'
    const rootNode = graphData.value.nodes.find((n) => n.id === 'root')
    if (rootNode?.label) {
      selectedKnowledgePoint.value = rootNode.label
      if (currentPage.value === 'graph') {
        await loadMaterialsByKnowledgePoint(rootNode.label)
        void loadLearningSuggestionsFor(rootNode.label)
        void loadMajorRelevanceFor(rootNode.label)
      }
    }
    await nextTick()
    renderGraphChart()
  } catch (err) {
    graphError.value = err?.response?.data?.message || '知识图谱加载失败，请稍后重试。'
  } finally {
    graphLoading.value = false
  }
}

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
      if (params?.data?.id) {
        selectedNodeId.value = params.data.id
        const name = params.data.name
        selectedKnowledgePoint.value = name
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
const passwordPanelRef = ref(null)

const handlePasswordSave = async () => {
  if (!passwordPanelRef.value || !passwordPanelRef.value.submitChange) return
  const ok = await passwordPanelRef.value.submitChange()
  if (ok) {
    changePasswordVisible.value = false
  }
}
const openPasswordPage = () => {
  changePasswordVisible.value = true
}

ensureCourseSelection()

watch(selectedCourse, () => {
  loadGraph()
  schedulePersistStudentState()
})

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
      if (!graphData.value.nodes?.length) {
        await loadGraph()
      } else {
        await nextTick()
        renderGraphChart()
        const kp =
          selectedKnowledgePoint.value ||
          graphData.value.nodes.find((n) => n.id === 'root')?.label ||
          ''
        if (kp) {
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

onMounted(async () => {
  await loadStudentState()
  loadGraph()
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
  <section class="panel">
    <div class="panel-header">
      <h2>学生端 · 自主学习空间</h2>
    </div>

    <section v-if="currentPage === 'home'" class="panel-stack">
      <article class="result-card profile-hero-card">
        <div class="profile-hero-main">
          <div class="profile-avatar">{{ userInitial }}</div>
          <div>
              <h3>{{ profileForm.username || currentUser.username }}</h3>
          </div>
        </div>
        <div class="profile-hero-actions">
          <div class="grid-form">
              <label>
                统计课程
                <select v-model="selectedCourse">
                  <option v-for="course in availableCourses" :key="course" :value="course">{{ course }}</option>
                </select>
              </label>
              <!-- 已将个人资料编辑入口移至下方操作区 -->
          </div>
        </div>
      </article>

      <div class="profile-grid">
        <article class="result-card profile-overview-card">
          <h3>学习画像</h3>
          <div class="profile-stat-list">
            <div>
              <span>累计练习</span>
              <strong>{{ learningStats.total }}</strong>
            </div>
            <div>

            
              <span>掌握程度</span>
              <strong>{{ learningStats.mastery }}%</strong>
            </div>
            <div>
              <span>错题收藏</span>
              <strong>{{ filteredWrongBook.length }}</strong>
            </div>
          </div>
        </article>

        <article class="result-card profile-detail-card">
          <h3>资料设置</h3>
          <div class="grid-form">
              <label>
                用户名
                <div class="panel-subtitle">{{ profileForm.username || currentUser.username }}</div>
              </label>
              <label>
                学工号
                <div class="panel-subtitle">{{ currentUser.workId || '未设置' }}</div>
              </label>
              <label>
                邮箱
                <div class="panel-subtitle">{{ profileForm.email || currentUser.email }}</div>
              </label>
            <label>
              专业
              <div class="panel-subtitle">{{ selectedMajorDisplay || '未设置' }}</div>
            </label>
          </div>
          <div class="profile-btn-row">
            <button type="button" class="nav-btn" @click="openEditProfile">编辑资料</button>
            <button type="button" class="nav-btn" @click="openPasswordPage">修改密码</button>
            <button class="danger-btn profile-logout-btn" @click="emit('logout')">退出登录</button>
          </div>
          <p v-if="profileMessage" class="ok-text">{{ profileMessage }}</p>
        </article>
      </div>
    </section>

    <!-- 修改密码已改为模态窗口 -->

    <section v-if="currentPage === 'graph'" class="panel-stack">
      <article class="result-card">
        <h3 class="panel-title">{{ graphData.title }}</h3>
        <div class="inline-form">
          <button :disabled="graphLoading" @click="loadGraph">{{ graphLoading ? '加载中...' : '刷新图谱' }}</button>
        </div>
        <p v-if="graphError" class="error-text">{{ graphError }}</p>
        
        <div ref="graphChartRef" style="width: 100%; height: 420px;"></div>
      </article>

      <article v-if="selectedNode" class="result-card">
        <h3>{{ selectedNode.label }}</h3>
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
      </article>
    </section>

    <section v-if="currentPage === 'exercise'" class="panel-stack">
      <article class="result-card">
        <h3>出题与做题</h3>
        <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px">
          <label style="margin:0">出题方式：</label>
          <label style="display:flex;align-items:center;gap:6px"><input type="radio" value="single" v-model="examMode" /> 测试</label>
          <label style="display:flex;align-items:center;gap:6px"><input type="radio" value="batch" v-model="examMode" /> 组卷</label>
        </div>

        <div v-if="examMode === 'single'" class="grid-form three-col">
          <label>
            知识点（多选）
            <div style="display:flex;gap:8px;align-items:center;margin-top:6px;flex-wrap:wrap">
              <span style="display:flex;align-items:center;gap:6px;white-space:nowrap">
                <span class="panel-subtitle" style="margin:0">0级</span>
                <input
                  type="text"
                  class="match-height"
                  style="min-width:10em;background:#f0f4f8;border:1px solid #dde1e6;color:#334155;cursor:default"
                  readonly
                  :value="selectedCourse"
                  title="当前课程名，与知识图谱根节点一致；可直接点「添加」仅按课程出题"
                />
              </span>
              <select v-model="kpCascade1" class="match-height">
                <option value="">一级知识点</option>
                <option v-for="p in kpLevel1Options" :key="p.id" :value="p.pointName">{{ p.pointName }}</option>
              </select>
              <select v-model="kpCascade2" class="match-height" :disabled="!kpCascade1">
                <option value="">二级知识点</option>
                <option v-for="p in kpLevel2Options" :key="p.id" :value="p.pointName">{{ p.pointName }}</option>
              </select>
              <select v-model="kpCascade3" class="match-height" :disabled="!kpCascade2">
                <option value="">三级知识点</option>
                <option v-for="p in kpLevel3Options" :key="p.id" :value="p.pointName">{{ p.pointName }}</option>
              </select>
              <button type="button" class="match-button" @click="addTestPoint">添加</button>
            </div>
            <div class="selected-chips" style="margin-top:8px;display:flex;flex-wrap:wrap;gap:8px">
              <span
                v-for="p in testForm.selectedPoints"
                :key="p"
                class="chip"
              >{{ p }} <button class="chip-remove" type="button" @click="removeTestPoint(p)">×</button></span>
            </div>
          </label>
          <label>
            难度
            <select v-model="questionForm.difficulty" class="match-height">
              <option>基础</option>
              <option>中等</option>
              <option>拔高</option>
            </select>
          </label>
          <label>
            总题数
            <div class="panel-subtitle">{{ testTotalCount }} / 10</div>
          </label>
        </div>

        <div v-if="examMode === 'single'" style="margin-top:12px">
          <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end">
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              单选题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="testCounts.singleChoiceCount" />
            </label>
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              多选题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="testCounts.multiChoiceCount" />
            </label>
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              判断题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="testCounts.judgeCount" />
            </label>
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              填空题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="testCounts.fillCount" />
            </label>
          </div>

          <div class="inline-form" style="margin-top:12px">
            <button
              :disabled="testLoading || testTotalCount < 1 || testTotalCount > 10"
              @click="generateTest"
            >{{ testLoading ? '生成中...' : '开始测试' }}</button>
          </div>
          <p v-if="testError && !(testQuestions.length && !testSubmitted)" class="error-text">{{ testError }}</p>
        </div>

        <div v-if="examMode === 'batch'" style="margin-top:12px">
          <label>
            选择知识点（按图谱逐级选择后点击添加；可混选不同层级，亦可只选 0 级课程名）
            <div style="display:flex;gap:8px;align-items:center;margin-top:6px;flex-wrap:wrap">
              <span style="display:flex;align-items:center;gap:6px;white-space:nowrap">
                <span class="panel-subtitle" style="margin:0">0级</span>
                <input
                  type="text"
                  class="match-height"
                  style="min-width:10em;background:#f0f4f8;border:1px solid #dde1e6;color:#334155;cursor:default"
                  readonly
                  :value="selectedCourse"
                  title="当前课程名；未选一二三级时点击「添加」即按课程出题"
                />
              </span>
              <select v-model="kpCascade1" class="match-height">
                <option value="">一级知识点</option>
                <option v-for="p in kpLevel1Options" :key="'e-' + p.id" :value="p.pointName">{{ p.pointName }}</option>
              </select>
              <select v-model="kpCascade2" class="match-height" :disabled="!kpCascade1">
                <option value="">二级知识点</option>
                <option v-for="p in kpLevel2Options" :key="'e-' + p.id" :value="p.pointName">{{ p.pointName }}</option>
              </select>
              <select v-model="kpCascade3" class="match-height" :disabled="!kpCascade2">
                <option value="">三级知识点</option>
                <option v-for="p in kpLevel3Options" :key="'e-' + p.id" :value="p.pointName">{{ p.pointName }}</option>
              </select>
              <button type="button" class="match-button" @click="addExamPoint">添加</button>
            </div>
            <div class="selected-chips" style="margin-top:8px;display:flex;flex-wrap:wrap;gap:8px">
              <span v-for="p in examForm.selectedPoints" :key="p" class="chip">{{ p }} <button class="chip-remove" @click="removeExamPoint(p)">×</button></span>
            </div>
          </label>

          <div style="display:flex;gap:12px;align-items:center;margin-top:12px">
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              单选题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="examForm.singleChoiceCount" />
            </label>
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              多选题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="examForm.multiChoiceCount" />
            </label>
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              判断题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="examForm.judgeCount" />
            </label>
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              填空题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="examForm.fillCount" />
            </label>
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              简答题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="examForm.shortCount" />
            </label>
            <label style="display:flex;flex-direction:column;align-items:flex-start">
              解答题数量
              <input class="small-input" type="number" min="0" max="10" v-model.number="examForm.essayCount" />
            </label>
          </div>

          <div class="grid-form two-col" style="margin-top:12px">
            <label>
              试卷标题
              <input v-model="examForm.title" placeholder="可选，默认为课程+试卷" />
            </label>
          </div>
        </div>
        <div v-if="examMode === 'batch'" class="inline-form" style="margin-top:8px">
          <button :disabled="examLoading" @click="generateExam">{{ examLoading ? '生成中...' : '生成试卷' }}</button>
        </div>
        <p v-if="examError" class="error-text">{{ examError }}</p>
        <div v-if="examMode === 'batch' && examResult && examResult.length" class="inline-form" style="margin-top:8px">
          <button :disabled="examLoading" @click="persistGeneratedExam">保存试卷并生成 MD</button>
        </div>
      </article>

      <article
        v-if="examMode === 'single' && testQuestions.length && !testSubmitted"
        class="result-card"
      >
        <h3>测试题目</h3>
        <p class="panel-subtitle">作答完成后点击“提交并查看成绩与解析”。</p>

        <div v-for="(q, idx) in testQuestions" :key="idx" style="margin-top:14px;">
          <h4>第 {{ idx + 1 }} 题（{{ q.question_type }}）</h4>
          <div class="latex-block" v-html="renderLatexText(q.question)"></div>

          <div v-if="q.question_type === '选择题'" class="option-list">
            <label v-for="opt in q.options || []" :key="opt" class="option-item">
              <input
                type="radio"
                :name="'test-q-' + idx"
                :value="parseOptionLetter(opt)"
                v-model="testAnswers[idx]"
              />
              <span v-html="renderLatexText(parseOptionText(opt))"></span>
            </label>
          </div>

          <div v-else-if="q.question_type === '多选题'" class="option-list">
            <label v-for="opt in q.options || []" :key="opt" class="option-item">
              <input
                type="checkbox"
                :value="parseOptionLetter(opt)"
                v-model="testAnswers[idx]"
              />
              <span v-html="renderLatexText(parseOptionText(opt))"></span>
            </label>
          </div>

          <div v-else-if="q.question_type === '判断题'" class="option-list">
            <label v-for="opt in q.options || []" :key="opt" class="option-item">
              <input
                type="radio"
                :name="'test-q-' + idx"
                :value="parseOptionLetter(opt)"
                v-model="testAnswers[idx]"
              />
              <span v-html="renderLatexText(parseOptionText(opt))"></span>
            </label>
          </div>

          <div v-else-if="q.question_type === '填空题'">
            <label>
              填空答案
              <input class="match-height" v-model="testAnswers[idx]" placeholder="请输入答案" />
            </label>
          </div>
        </div>

        <div class="inline-form" style="margin-top:12px;">
          <button :disabled="testLoading" @click="submitTest">提交并查看成绩与解析</button>
        </div>
        <p v-if="testError" class="error-text">{{ testError }}</p>
      </article>

      <article
        v-if="examMode === 'single' && testSubmitted && testResult"
        class="result-card"
      >
        <h3>成绩与解析</h3>
        <p><strong>总分：</strong>{{ testResult.totalScore }} / {{ testResult.fullScore }}</p>

        <div v-for="(q, idx) in testQuestions" :key="idx" style="margin-top:14px;">
          <h4>第 {{ idx + 1 }} 题</h4>
          <p class="panel-subtitle">
            得分：{{ (testResult.perQuestionScores[idx]?.score) || 0 }} / 10
          </p>
          <div class="latex-block" v-html="renderLatexText(q.question)"></div>
          <p><strong>你的答案：</strong><span v-html="renderLatexText(resolveAnswerText(q, testAnswers[idx]))"></span></p>
          <p><strong>正确答案：</strong><span v-html="renderLatexText(resolveAnswerText(q, q.answer))"></span></p>

          <div style="margin-top:10px;">
            <h4>解析</h4>
            <div class="latex-block" v-html="renderLatexText(q.explanation)"></div>
          </div>
        </div>
      </article>
    </section>

    <section v-if="currentPage === 'review'" class="panel-stack">
      <article class="result-card">
        <h3>错题本</h3>
        <p v-if="!filteredWrongBook.length" class="panel-subtitle">当前课程暂无收藏错题。</p>
        <div v-else class="wrong-book-grid">
          <article v-for="item in filteredWrongBook" :key="item.id" class="wrong-book-card">
            <div class="wrong-book-card-top">
              <strong class="wrong-book-title">{{ item.course }} · {{ item.knowledgePoint }}</strong>
              <div class="wrong-book-meta-line">
                <span>{{ item.score }} / {{ item.fullScore }} 分</span>
                <span class="wrong-book-time">{{ item.collectedAt }}</span>
              </div>
            </div>
            <p class="wrong-book-preview">{{ wrongBookQuestionPreview(item.question) }}</p>
            <div class="wrong-book-card-actions">
              <button type="button" class="match-button wrong-book-toggle" @click.stop="openWrongBookModal(item)">
                查看题目与解析
              </button>
              <button type="button" class="cancel-button" @click.stop="removeWrongItem(item.id)">删除</button>
            </div>
          </article>
        </div>

        <Teleport to="body">
          <div v-if="wrongBookModalItem" class="modal-mask" @click.self="closeWrongBookModal">
            <div class="modal-wrapper wrong-book-modal-wrap">
              <div class="modal-container wrong-book-modal-box">
                <button type="button" class="modal-close" @click="closeWrongBookModal" aria-label="关闭">×</button>
                <h3>题目与解析</h3>
                <p class="panel-subtitle wrong-book-modal-sub">
                  {{ wrongBookModalItem.course }} · {{ wrongBookModalItem.knowledgePoint }}
                </p>
                <div class="wrong-book-modal-body">
                  <div class="latex-block wrong-book-detail-q" v-html="renderLatexText(wrongBookModalItem.question)"></div>
                  <p class="wrong-book-detail-row">
                    <strong>我的答案：</strong><span v-html="renderLatexText(wrongBookModalItem.myAnswer)"></span>
                  </p>
                  <p class="wrong-book-detail-row">
                    <strong>参考答案：</strong><span v-html="renderLatexText(wrongBookModalItem.answer)"></span>
                  </p>
                  <div v-if="wrongBookModalItem.explanation" class="wrong-book-detail-explain">
                    <h4 class="wrong-book-explain-heading">解析</h4>
                    <div class="latex-block" v-html="renderLatexText(wrongBookModalItem.explanation)"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Teleport>
      </article>

      <article class="result-card">
        <h3>学习记录</h3>
        <p v-if="!filteredLearningRecords.length" class="panel-subtitle">当前课程暂无学习记录。</p>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>课程</th>
              <th>知识点</th>
              <th>得分</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredLearningRecords" :key="item.id">
              <td>{{ item.time }}</td>
              <td>{{ item.course }}</td>
              <td>{{ item.knowledgePoint }}</td>
              <td>{{ item.score }} / {{ item.fullScore }}</td>
              <td>
                <button type="button" @click="removeLearningRecord(item.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </article>

      <article class="result-card">
        <h3>已保存试卷</h3>
        <p v-if="!savedExams.length" class="panel-subtitle">暂无已保存试卷。</p>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>标题</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in savedExams" :key="e.id">
              <td>{{ e.title || ('试卷-' + e.id) }}</td>
              <td>{{ e.createdAt ? new Date(e.createdAt).toLocaleString() : '-' }}</td>
              <td>
                <div style="display:flex;gap:8px;align-items:center">
                  <button :disabled="!e.mdPaper" @click="downloadExam(e.id, 'md_paper')" :title="e.mdPaper ? '下载原卷 (Markdown)' : 'Markdown 未生成'">下载 MD</button>
                  <button v-if="!(e.mdPaper && e.mdAnswer)" @click="renderExamPdfs(e.id)" title="生成 Markdown 文件">生成 MD</button>
                  <button :disabled="!e.mdAnswer" @click="downloadExam(e.id, 'md_answer')" :title="e.mdAnswer ? '下载答案 (Markdown)' : 'Markdown 未生成'">下载 答案 (MD)</button>
                  <button @click="confirmDeleteExam(e.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="examError" class="error-text" style="margin-top:8px">{{ examError }}</p>
      </article>
    </section>

    <section v-if="currentPage === 'announcements'" class="panel-stack">
      <article class="result-card">
        <h3>平台公告</h3>
        <p v-if="annLoading && !announcements.length" class="panel-subtitle">加载中…</p>
        <p v-else-if="annError" class="error-text">{{ annError }}</p>
        <p v-else-if="!announcements.length" class="panel-subtitle">暂无公告。</p>
        <div v-else class="announcement-read-list">
          <article v-for="a in announcements" :key="a.id" class="result-card announcement-read-card">
            <h4 class="announcement-read-title">{{ a.title }}</h4>
            <p class="panel-subtitle announcement-read-meta">
              <span v-if="a.publisherName">{{ a.publisherName }}</span>
              <span v-if="a.publisherName && a.createdAt"> · </span>
              <span>{{ formatAnnTime(a.createdAt) }}</span>
            </p>
            <div class="announcement-read-body">{{ a.content }}</div>
          </article>
        </div>
      </article>
    </section>

<!-- 编辑个人资料模态框 -->
<div v-if="editProfileVisible" class="modal-mask" @click.self="editProfileVisible = false">
  <div class="modal-wrapper">
    <div class="modal-container">
      <button class="modal-close" @click="editProfileVisible = false" aria-label="关闭">×</button>
      <h3>编辑个人资料</h3>
      <div class="grid-form single-col" style="margin-top:12px;">
        <label>
          用户名
          <input v-model="editProfileForm.username" class="match-height" />
        </label>
        <label>
          邮箱
          <input v-model="editProfileForm.email" type="email" class="match-height" />
        </label>
        <label>
          专业
          <div style="display:flex;gap:8px;">
            <select v-model="editProfileForm.major1" @change="onEditMajor1Change">
              <option value="">请选择</option>
              <option v-for="m in majorLevel1" :key="m.code" :value="m.code">{{ m.name }}</option>
            </select>
            <select v-model="editProfileForm.major2" :disabled="!(Array.isArray(majorLevel2) && majorLevel2.length)" @change="onEditMajor2Change">
              <option value="">请选择</option>
              <option v-for="m in (majorLevel2 || [])" :key="m.code" :value="m.code">{{ m.name }}</option>
            </select>
            <select v-model="editProfileForm.major3" :disabled="!(Array.isArray(majorLevel3) && majorLevel3.length)">
              <option value="">请选择</option>
              <option v-for="m in (majorLevel3 || [])" :key="m.code" :value="m.code">{{ m.name }}</option>
            </select>
          </div>
        </label>
      </div>
      <div style="display:flex;gap:8px;margin-top:12px;">
        <button class="match-height match-button" @click="handleSaveProfile">保存</button>
        <button class="match-height cancel-button" @click="editProfileVisible = false">取消</button>
      </div>
    </div>
  </div>
</div>
    <!-- 修改密码模态框 -->
    <div v-if="changePasswordVisible" class="modal-mask" @click.self="changePasswordVisible = false">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" @click="changePasswordVisible = false" aria-label="关闭">×</button>
          <h3>修改密码</h3>
          <div style="margin-top:12px;">
            <AccountSecurityPanel ref="passwordPanelRef" :current-user="currentUser" :embedded="true" />
          </div>
          <div style="display:flex;gap:8px;margin-top:12px;">
            <button class="match-height match-button" @click="handlePasswordSave">保存</button>
            <button class="match-height cancel-button" @click="changePasswordVisible = false">取消</button>
          </div>
        </div>
      </div>
    </div>
    </section>
</template>

<style scoped src="./student-portal.css"></style>
