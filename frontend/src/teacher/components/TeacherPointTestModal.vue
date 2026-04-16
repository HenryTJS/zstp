<script setup>
import { nextTick, ref, watch } from 'vue'
import {
  fetchQuestion,
  askAiAgent,
  saveKnowledgePointPublishedTest,
  getKnowledgePointPublishedTestForTeacher
} from '../../api/client'

const props = defineProps({
  visible: { type: Boolean, default: false },
  courseName: { type: String, default: '' },
  pointName: { type: String, default: '' },
  /** 锚点为课程根时用于默认标题「期末测试」 */
  isCourseRootAnchor: { type: Boolean, default: false },
  /** 锚点及其下属知识点，供每题选择命题语境 */
  topicPointOptions: { type: Array, default: () => [] },
  teacherId: { type: [Number, String], default: null },
  teacherUsername: { type: String, default: '' }
})

const emit = defineEmits(['close'])

const title = ref('')
const questionCount = ref(3)
const questions = ref([])
const saving = ref(false)
const loadError = ref('')
const saveMessage = ref('')

const anchorPn = () => String(props.pointName || '').trim()

const mergedTopicPointOptions = () => {
  const a = anchorPn()
  const out = []
  if (a) out.push({ value: a, label: a })
  for (const o of props.topicPointOptions || []) {
    if (!o?.value) continue
    const v = String(o.value)
    if (out.some((x) => x.value === v)) continue
    out.push({ value: v, label: String(o.label || o.value) })
  }
  return out
}

const allowedFocusNames = () => {
  const a = anchorPn()
  const s = new Set()
  if (a) s.add(a)
  for (const o of props.topicPointOptions || []) {
    if (o?.value) s.add(String(o.value))
  }
  return s
}

const createEmptyQuestionSlot = () => ({
  question_type: '选择题',
  fullScore: 5,
  question: '',
  options: ['', '', '', ''],
  answer: '',
  explanation: '',
  focusPointName: anchorPn(),
  aiLoading: false,
  explainLoading: false
})

const syncSlots = (n) => {
  const target = Math.min(30, Math.max(1, Number(n) || 1))
  const cur = questions.value || []
  const anchor = anchorPn()
  const next = []
  for (let i = 0; i < target; i++) {
    if (cur[i]) {
      const c = {
        ...cur[i],
        options: cur[i].options?.length === 4 ? [...cur[i].options] : ['', '', '', '']
      }
      if (!String(c.focusPointName || '').trim()) c.focusPointName = anchor
      next.push(c)
    } else {
      next.push(createEmptyQuestionSlot())
    }
  }
  questions.value = next
}

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    loadError.value = ''
    saveMessage.value = ''
    const cn = (props.courseName || '').trim()
    const pn = anchorPn()
    if (!cn || !pn || !props.teacherId) return
    try {
      const { data } = await getKnowledgePointPublishedTestForTeacher({
        courseName: cn,
        pointName: pn,
        teacherUserId: props.teacherId
      })
      const t = data?.test
      if (t && Array.isArray(t.questions) && t.questions.length) {
        title.value = t.title || ''
        questionCount.value = t.questions.length
        questions.value = t.questions.map((q) => ({
          question_type: q.question_type === '填空题' ? '填空题' : '选择题',
          fullScore: Number(q.fullScore) || 5,
          question: q.question || '',
          options: Array.isArray(q.options) && q.options.length ? [...q.options] : ['', '', '', ''],
          answer: q.answer || '',
          explanation: q.explanation || '',
          focusPointName: String(q.focusPointName || '').trim() || pn,
          aiLoading: false,
          explainLoading: false
        }))
      } else {
        title.value = props.isCourseRootAnchor ? `${cn} · 期末测试` : `${pn} · 教师测试`
        questionCount.value = 3
        syncSlots(3)
      }
    } catch (e) {
      loadError.value = e?.response?.data?.message || '加载已有测试失败'
      title.value = props.isCourseRootAnchor ? `${cn} · 期末测试` : `${pn} · 教师测试`
      questionCount.value = 3
      syncSlots(3)
    }
  }
)

watch(questionCount, (n) => syncSlots(n))

const topicForQuestion = (idx) => {
  const cn = (props.courseName || '').trim()
  const q = questions.value[idx]
  const focus = String(q?.focusPointName || props.pointName || '').trim()
  // 明确约束：只围绕所选知识点命题，避免 AI 跑到相邻/上级/下级点
  const base = `${cn} 知识点：${focus}`.trim()
  return `${base}（第${idx + 1}题；要求：仅考查该知识点，不要引入其它知识点或跨章节内容）`
}

const onAiQuestion = async (idx) => {
  const q = questions.value[idx]
  if (!q) return
  // 关键：确保“下拉选择的知识点”已同步到响应式状态，再生成
  // （避免用户刚选完就点按钮，仍用旧 focusPointName）
  await nextTick()
  q.aiLoading = true
  try {
    const cn = (props.courseName || '').trim()
    const focus = String(q?.focusPointName || props.pointName || '').trim()
    const topic =
      `${cn} 知识点：${focus}`.trim() +
      `（第${idx + 1}题；要求：仅考查该知识点，不要引入其它知识点或跨章节内容）`
    const { data } = await fetchQuestion({
      topic,
      difficulty: '中等',
      questionType: q.question_type,
      major: null
    })
    const d = data || {}
    q.question = String(d.question || '').trim() || q.question
    q.options = Array.isArray(d.options) ? d.options.map((x) => String(x)) : q.options
    if (q.question_type === '选择题' && (!q.options || q.options.length < 2)) {
      q.options = ['A. ', 'B. ', 'C. ', 'D. ']
    }
    q.answer = String(d.answer || '').trim() || q.answer
    q.explanation = String(d.explanation || '').trim() || q.explanation
  } catch (e) {
    loadError.value = e?.response?.data?.message || 'AI 生题失败'
  } finally {
    q.aiLoading = false
  }
}

const onAiExplanation = async (idx) => {
  const q = questions.value[idx]
  if (!q || !String(q.question || '').trim() || !String(q.answer || '').trim()) {
    loadError.value = '请先填写题干与参考答案，再生成解析。'
    return
  }
  q.explainLoading = true
  loadError.value = ''
  try {
    const { data } = await askAiAgent({
      question:
        '请为以下题目写简短解析（面向学生，用中文，可含简单 LaTeX 用 $ 包裹）：\n题干：' +
        q.question +
        '\n参考答案：' +
        q.answer,
      role: 'teacher',
      userId: String(props.teacherId || ''),
      username: props.teacherUsername || ''
    })
    const ans = data?.answer || data?.message || ''
    q.explanation = String(ans || '').trim()
  } catch (e) {
    loadError.value = e?.response?.data?.message || 'AI 生成解析失败'
  } finally {
    q.explainLoading = false
  }
}

const publish = async () => {
  const cn = (props.courseName || '').trim()
  const pn = anchorPn()
  if (!cn || !pn || !props.teacherId) return
  const allowed = allowedFocusNames()
  for (let i = 0; i < questions.value.length; i++) {
    const q = questions.value[i]
    if (!String(q.question || '').trim()) {
      saveMessage.value = ''
      loadError.value = `请完善第 ${i + 1} 题题干`
      return
    }
    if (!String(q.answer || '').trim()) {
      saveMessage.value = ''
      loadError.value = `请填写第 ${i + 1} 题答案`
      return
    }
    const fp = String(q.focusPointName || '').trim() || pn
    if (!allowed.has(fp)) {
      loadError.value = `第 ${i + 1} 题所选考查知识点不在当前锚点下属范围内`
      return
    }
    if (q.question_type === '选择题') {
      const opts = (q.options || []).map((s) => String(s || '').trim()).filter(Boolean)
      if (opts.length < 2) {
        loadError.value = `第 ${i + 1} 题选择题至少保留 2 个有效选项`
        return
      }
      q.options = opts
    } else {
      q.options = []
    }
  }
  saving.value = true
  loadError.value = ''
  saveMessage.value = ''
  const defaultTitle = props.isCourseRootAnchor ? `${cn} · 期末测试` : `${pn} · 教师测试`
  try {
    const payload = {
      teacherUserId: props.teacherId,
      courseName: cn,
      pointName: pn,
      title: (title.value || '').trim() || defaultTitle,
      questions: questions.value.map((q) => {
        const fp = String(q.focusPointName || '').trim() || pn
        const rawAns = String(q.answer || '').trim()
        const normalizedAnswer =
          q.question_type === '选择题'
            ? (rawAns.toUpperCase().match(/[A-D]/)?.[0] || rawAns)
            : rawAns
        return {
          question_type: q.question_type,
          question: q.question.trim(),
          options: q.question_type === '选择题' ? q.options : [],
          answer: normalizedAnswer,
          explanation: String(q.explanation || '').trim(),
          fullScore: Math.min(100, Math.max(1, Number(q.fullScore) || 5)),
          focusPointName: fp
        }
      })
    }
    await saveKnowledgePointPublishedTest(payload)
    saveMessage.value = '已发布，学生端「练习」页可见。'
  } catch (e) {
    loadError.value = e?.response?.data?.message || '发布失败'
  } finally {
    saving.value = false
  }
}

const close = () => emit('close')
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="close">
    <div class="modal-wrapper" style="max-width: 720px; width: 94vw">
      <div class="modal-container" style="max-height: 88vh; overflow: auto">
        <button class="modal-close" type="button" aria-label="关闭" @click="close">×</button>
        <h3 class="portal-section-title portal-section-title--amber">发布知识点测试</h3>

        <label class="block-label">
          试卷标题
          <input v-model="title" class="match-height" style="width: 100%; box-sizing: border-box" />
        </label>

        <label class="block-label" style="margin-top: 10px">
          题目数量（1～30）
          <input v-model.number="questionCount" type="number" min="1" max="30" class="match-height" />
        </label>

        <p v-if="loadError" class="error-text">{{ loadError }}</p>
        <p v-if="saveMessage" class="ok-text">{{ saveMessage }}</p>

        <div v-for="(q, idx) in questions" :key="idx" class="tpt-slot">
          <h4>第 {{ idx + 1 }} 题</h4>
          <label class="block-label">
            本题考查知识点
            <select v-model="q.focusPointName" class="match-height" style="width: 100%; box-sizing: border-box">
              <option v-for="o in mergedTopicPointOptions()" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <div class="grid-form two-col">
            <label>
              题型
              <select v-model="q.question_type" class="match-height">
                <option value="选择题">单选题</option>
                <option value="填空题">填空题</option>
              </select>
            </label>
            <label>
              分值
              <input v-model.number="q.fullScore" type="number" min="1" max="100" class="match-height" />
            </label>
          </div>
          <div class="inline-form tpt-actions-inline">
            <button type="button" class="cancel-button" :disabled="q.aiLoading" @click="onAiQuestion(idx)">
              {{ q.aiLoading ? '生成中…' : 'AI 生成本题' }}
            </button>
            <button type="button" class="cancel-button" :disabled="q.explainLoading" @click="onAiExplanation(idx)">
              {{ q.explainLoading ? '解析中…' : 'AI 生成解析' }}
            </button>
          </div>
          <label class="block-label">
            题干
            <textarea v-model="q.question" rows="3" style="width: 100%; box-sizing: border-box"></textarea>
          </label>
          <template v-if="q.question_type === '选择题'">
            <p class="panel-subtitle">选项（至少 2 项）</p>
            <label v-for="(op, oi) in q.options" :key="oi" class="block-label">
              选项 {{ oi + 1 }}
              <input v-model="q.options[oi]" class="match-height" style="width: 100%; box-sizing: border-box" />
            </label>
          </template>
          <label class="block-label">
            参考答案（选择题为选项字母如 A；填空题为标准答案）
            <input v-model="q.answer" class="match-height" style="width: 100%; box-sizing: border-box" />
          </label>
          <label class="block-label">
            解析（可空）
            <textarea v-model="q.explanation" rows="2" style="width: 100%; box-sizing: border-box"></textarea>
          </label>
        </div>

        <div class="inline-form tpt-actions-submit">
          <button type="button" class="match-button" :disabled="saving" @click="publish">
            {{ saving ? '发布中…' : '发布测试' }}
          </button>
          <button type="button" class="cancel-button" @click="close">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.block-label {
  display: block;
  margin-top: 8px;
  font-size: 0.9rem;
}
.tpt-slot {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
}
.tpt-actions-inline{ margin-top:8px; }
.tpt-actions-submit{ margin-top:16px; }
.ok-text {
  color: #15803d;
  margin-top: 8px;
}
</style>
<style>
@import '@/teacher/styles/teacher-portal.css';
</style>
