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
const emit = defineEmits(['navigate', 'logout'])

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
import * as echarts from 'echarts'
import katex from 'katex'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AccountSecurityPanel from './AccountSecurityPanel.vue'
import { fetchGrading, fetchKnowledgeGraph, fetchQuestion, fetchQuestions, fetchStudentState, saveStudentState, fetchMaterialsByKnowledgePoint, updateUser, listKnowledgePoints, fetchExam, fetchExams, deleteExam, fetchLearningSuggestions, saveExam } from '../api/client'
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

// 加载当前课程的知识点供多选使用
const examPointOptions = ref([])
const loadExamPoints = async () => {
  try {
    const { data } = await listKnowledgePoints(selectedCourse.value)
    examPointOptions.value = Array.isArray(data) ? data.map(p => p.pointName) : (Array.isArray(data?.points) ? data.points.map(p=>p.pointName) : [])
  } catch (e) {
    examPointOptions.value = []
  }
}
watch(selectedCourse, () => loadExamPoints(), { immediate: true })

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

const testPointPicker = ref('')

const addTestPoint = () => {
  const p = testPointPicker.value
  if (!p) return
  if (!Array.isArray(testForm.value.selectedPoints)) testForm.value.selectedPoints = []
  if (!testForm.value.selectedPoints.includes(p)) {
    testForm.value.selectedPoints.push(p)
  }
  testPointPicker.value = ''
}

const removeTestPoint = (p) => {
  if (!Array.isArray(testForm.value.selectedPoints)) return
  testForm.value.selectedPoints = testForm.value.selectedPoints.filter(x => x !== p)
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
      .map((q) => {
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
      const kp = Array.isArray(q.knowledge_points) && q.knowledge_points.length
        ? String(q.knowledge_points[0] || '').trim()
        : (Array.isArray(testForm.value.selectedPoints) && testForm.value.selectedPoints.length
          ? String(testForm.value.selectedPoints[i % testForm.value.selectedPoints.length] || '').trim()
          : '')

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
      mastery: 0,
      weakPoints: []
    }
  }

  const totalScore = filteredLearningRecords.value.reduce((sum, item) => sum + item.score, 0)
  const totalFull = filteredLearningRecords.value.reduce((sum, item) => sum + item.fullScore, 0)
  const average = Math.round((totalScore / filteredLearningRecords.value.length) * 10) / 10
  const mastery = totalFull > 0 ? Math.round((totalScore / totalFull) * 100) : 0

  const byPoint = {}
  for (const item of filteredLearningRecords.value) {
    if (!byPoint[item.knowledgePoint]) {
      byPoint[item.knowledgePoint] = { score: 0, full: 0 }
    }
    byPoint[item.knowledgePoint].score += item.score
    byPoint[item.knowledgePoint].full += item.fullScore
  }

  const weakPoints = Object.entries(byPoint)
    .map(([name, value]) => ({
      name,
      ratio: value.full > 0 ? Math.round((value.score / value.full) * 100) : 0
    }))
    .sort((a, b) => a.ratio - b.ratio)
    .slice(0, 3)

  return {
    total: filteredLearningRecords.value.length,
    average,
    mastery,
    weakPoints
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

// 临时单选器用于按教师端风格逐条添加知识点
const examPointPicker = ref('')
const addExamPoint = () => {
  const p = examPointPicker.value
  if (!p) return
  if (!Array.isArray(examForm.value.selectedPoints)) examForm.value.selectedPoints = []
  if (!examForm.value.selectedPoints.includes(p)) {
    examForm.value.selectedPoints.push(p)
  }
  examPointPicker.value = ''
}
const removeExamPoint = (p) => {
  if (!Array.isArray(examForm.value.selectedPoints)) return
  examForm.value.selectedPoints = examForm.value.selectedPoints.filter(x => x !== p)
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

    selectedNodeId.value = graphData.value.nodes.find((node) => node.id !== 'root')?.id || ''
    const initialNode = graphData.value.nodes.find((n) => n.id === selectedNodeId.value)
    if (initialNode?.label) {
      selectedKnowledgePoint.value = initialNode.label
      if (currentPage.value === 'graph') {
        void loadLearningSuggestionsFor(initialNode.label)
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
      if (params?.data?.id && params.data.id !== 'root') {
        selectedNodeId.value = params.data.id
        // 选中知识点时自动加载资料
        selectedKnowledgePoint.value = params.data.name
        loadMaterialsByKnowledgePoint(params.data.name)
        // 同步生成“学习建议”（基于当前知识点）
        void loadLearningSuggestionsFor(params.data.name)
      }
    })
  const loadMaterialsByKnowledgePoint = async (pointName) => {
    if (!selectedCourse.value || !pointName) {
      materials.value = []
      return
    }
    const resp = await fetchMaterialsByKnowledgePoint(selectedCourse.value, pointName, false)
    materials.value = resp.data
  }
  // 页面切换到知识图谱时，自动加载根节点资料
  watch(currentPage, (val) => {
    if (val === 'graph' && graphData.value.nodes?.length) {
      const rootNode = graphData.value.nodes.find((n) => n.id !== 'root') || graphData.value.nodes[0]
      if (rootNode) {
        selectedKnowledgePoint.value = rootNode.label
        loadMaterialsByKnowledgePoint(rootNode.label)
        // 同步生成“学习建议”（基于当前知识点）
        void loadLearningSuggestionsFor(rootNode.label)
      }
    }
  })
      // ...existing code...
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
  schedulePersistStudentState()
}

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
    emit('update-user', { username: updatedUser.username, email: updatedUser.email })
  } catch (err) {
    // 保持本地更新，向用户展示错误信息
    editProfileVisible.value = false
    profileMessage.value = err?.response?.data?.message || '同步用户信息到服务器失败，请稍后重试。'
    // 即使后端同步失败，也更新 localStorage 显示最新输入，避免用户界面和输入不一致
    const fallbackUser = { ...props.currentUser, username: profileForm.value.username, email: profileForm.value.email }
    try { localStorage.setItem('currentUser', JSON.stringify(fallbackUser)) } catch (e) {}
    emit('update-user', { username: profileForm.value.username, email: profileForm.value.email })
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

// removed watcher for profileForm.bio (learning goal removed)

watch(
  () => currentPage.value,
  async (value) => {
    if (value === 'review') {
      await loadSavedExams()
    }
    if (value === 'graph') {
      if (!graphData.value.nodes?.length) {
        await loadGraph()
      }
      await nextTick()
      renderGraphChart()
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
          <div>
            <h3>薄弱点提示</h3>
            <p v-if="!learningStats.weakPoints.length" class="panel-subtitle">暂无历史练习。</p>
            <ul v-else>
              <li v-for="item in learningStats.weakPoints" :key="item.name">{{ item.name }}（掌握率 {{ item.ratio }}%）</li>
            </ul>
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
            <div style="display:flex;gap:8px;align-items:center;margin-top:6px">
              <select v-model="testPointPicker" class="match-height">
                <option value="">请选择知识点</option>
                <option v-for="p in examPointOptions" :key="p" :value="p">{{ p }}</option>
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
            选择知识点（下拉选择后点击添加）
            <div style="display:flex;gap:8px;align-items:center;margin-top:6px">
              <select v-model="examPointPicker">
                <option value="">请选择知识点</option>
                <option v-for="p in examPointOptions" :key="p" :value="p">{{ p }}</option>
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
        <div v-else class="panel-stack">
          <article v-for="item in filteredWrongBook" :key="item.id" class="result-card wrong-item-card">
            <p><strong>{{ item.major }} · {{ item.course }} · {{ item.knowledgePoint }}</strong></p>
            <p>{{ item.question }}</p>
            <p><strong>我的答案：</strong>{{ item.myAnswer }}</p>
            <p><strong>参考答案：</strong>{{ item.answer }}</p>
            <p><strong>得分：</strong>{{ item.score }} / {{ item.fullScore }}</p>
            <p><strong>收藏时间：</strong>{{ item.collectedAt }}</p>
            <button type="button" @click="removeWrongItem(item.id)">删除</button>
          </article>
        </div>
      </article>

      <article class="result-card">
        <h3>学习记录</h3>
        <p v-if="!filteredLearningRecords.length" class="panel-subtitle">当前课程暂无学习记录。</p>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>专业</th>
              <th>课程</th>
              <th>知识点</th>
              <th>得分</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredLearningRecords" :key="item.id">
              <td>{{ item.time }}</td>
              <td>{{ item.major }}</td>
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
