<script setup>
import { computed, ref, watch } from 'vue'
import { fetchQuestion, listKnowledgePoints, saveExam } from '../api/client'

const props = defineProps({
  joinedCourses: { type: Array, required: true },
  selectedMajor: { type: String, default: '' },
  renderLatexText: { type: Function, required: true },
  refreshSavedExams: { type: Function, required: true }
})

const emit = defineEmits(['go-courses'])

const TYPE_OPTIONS = [
  { value: '单选', label: '单选' },
  { value: '多选', label: '多选' },
  { value: '判断', label: '判断' },
  { value: '填空', label: '填空' },
  { value: '简答', label: '简答' },
  { value: '解答', label: '解答' }
]

const unescapeNewlinesSafe = (t) => {
  if (t === null || t === undefined) return t
  return String(t)
}

const normalizeExamQuestionType = (label) => {
  const s = String(label || '').trim()
  if (s === '单选') return '选择题'
  if (s === '多选') return '多选题'
  if (s === '判断') return '判断题'
  if (s === '填空') return '填空题'
  if (s === '简答') return '简答题'
  if (s === '解答') return '解答题'
  return '解答题'
}

const normalizeQuestionPayload = (q) => {
  if (!q) return q
  return {
    ...q,
    question: unescapeNewlinesSafe(q.question),
    explanation: unescapeNewlinesSafe(q.explanation),
    answer: unescapeNewlinesSafe(q.answer),
    options: Array.isArray(q.options) ? q.options.map(unescapeNewlinesSafe) : [],
    knowledge_points: Array.isArray(q.knowledge_points) ? q.knowledge_points.map(unescapeNewlinesSafe) : []
  }
}

const sortPoints = (a, b) => {
  const oa = Number(a?.sortOrder ?? 0)
  const ob = Number(b?.sortOrder ?? 0)
  if (oa !== ob) return oa - ob
  return String(a?.pointName || '').localeCompare(String(b?.pointName || ''), 'zh-CN')
}

const paperCourse = ref('')
const paperDifficulty = ref('中等')
const paperTitle = ref('')
const paperPointsRaw = ref([])
const paperError = ref('')
const saving = ref(false)

const nextRowId = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `r-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

const clampFullScore = (v) => {
  const n = Number(v)
  const x = Number.isFinite(n) ? Math.round(n) : 10
  return Math.min(100, Math.max(1, x))
}

const makeRow = () => ({
  id: nextRowId(),
  typeKey: '单选',
  pointName: '',
  fullScore: 10,
  question: null,
  loading: false,
  rowError: ''
})

const rows = ref([makeRow()])

watch(
  () => props.joinedCourses,
  (list) => {
    const arr = Array.isArray(list) ? list : []
    if (!paperCourse.value && arr.length) {
      paperCourse.value = String(arr[0] || '').trim()
    }
    if (paperCourse.value && !arr.includes(paperCourse.value)) {
      paperCourse.value = arr.length ? String(arr[0] || '').trim() : ''
    }
  },
  { immediate: true }
)

const loadPaperPoints = async () => {
  paperPointsRaw.value = []
  const cn = String(paperCourse.value || '').trim()
  if (!cn) return
  try {
    const { data } = await listKnowledgePoints(cn)
    const points = Array.isArray(data?.points) ? data.points : Array.isArray(data) ? data : []
    paperPointsRaw.value = points.slice().sort(sortPoints)
  } catch {
    paperPointsRaw.value = []
  }
}

watch(paperCourse, () => {
  void loadPaperPoints()
  for (const r of rows.value) {
    r.question = null
    r.rowError = ''
  }
})

const pointSelectOptions = computed(() => {
  const cn = String(paperCourse.value || '').trim()
  const names = new Set()
  if (cn) names.add(cn)
  for (const p of paperPointsRaw.value) {
    const n = String(p?.pointName || '').trim()
    if (n) names.add(n)
  }
  return Array.from(names).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

const previewRows = computed(() => rows.value.filter((r) => r.question))

const downloadExam = (id, type) => {
  window.open('/api/exams/' + id + '/download?type=' + type)
}

const generateRow = async (row) => {
  row.rowError = ''
  const cn = String(paperCourse.value || '').trim()
  if (!cn) {
    row.rowError = '请先选择课程'
    return
  }
  const kp = String(row.pointName || '').trim()
  if (!kp) {
    row.rowError = '请选择知识点'
    return
  }
  row.loading = true
  try {
    const idx = rows.value.indexOf(row) + 1
    const topic = `${props.selectedMajor} ${cn} ${kp}`.trim() + `（卷${idx}）`
    const { data } = await fetchQuestion({
      topic,
      difficulty: paperDifficulty.value || '中等',
      questionType: normalizeExamQuestionType(row.typeKey),
      major: props.selectedMajor || null
    })
    row.question = normalizeQuestionPayload(data)
  } catch (err) {
    row.rowError = err?.response?.data?.message || '题目生成失败，请稍后重试。'
  } finally {
    row.loading = false
  }
}

const addRow = () => {
  if (rows.value.length >= 15) return
  rows.value.push(makeRow())
}

const removeRow = (idx) => {
  if (rows.value.length <= 1) return
  rows.value.splice(idx, 1)
}

const persistPaper = async () => {
  paperError.value = ''
  const list = previewRows.value.map((r) => ({
    ...r.question,
    fullScore: clampFullScore(r.fullScore)
  }))
  if (!list.length) {
    paperError.value = '请至少成功生成一题后再保存。'
    return
  }
  saving.value = true
  try {
    const cn = String(paperCourse.value || '').trim()
    const payload = {
      title: String(paperTitle.value || '').trim() || (cn ? `${cn} 试卷` : '自定义试卷'),
      questions: list
    }
    const resp = await saveExam(payload)
    const data = resp && resp.data ? resp.data : resp
    await props.refreshSavedExams()
    if (data?.examId) {
      if (data.mdOriginalPresent) {
        try {
          downloadExam(data.examId, 'md_paper')
        } catch (e) {}
      }
      if (data.mdAnswerPresent) {
        try {
          downloadExam(data.examId, 'md_answer')
        } catch (e) {}
      }
    }
  } catch (err) {
    paperError.value = err?.response?.data?.message || '保存试卷失败，请稍后重试。'
  } finally {
    saving.value = false
  }
}

const goCourses = () => emit('go-courses')
</script>

<template>
  <section class="panel-stack">
    <article v-if="!joinedCourses.length" class="result-card">
      <h3>组卷</h3>
      <p class="panel-subtitle">请先在「课程广场」加入至少一门课程，再使用组卷功能。</p>
      <button type="button" class="match-button" @click="goCourses">去课程广场</button>
    </article>

    <template v-else>
      <article class="result-card">
        <h3>自定义组卷</h3>
        <p class="panel-subtitle" style="margin-top: 6px">
          每题可单独设置分值（1–100）；整张试卷最多 15 题。保存后 Markdown 中会显示各题分值。
        </p>

        <div class="grid-form two-col ui-mt-12">
          <label>
            课程
            <select v-model="paperCourse" class="match-height">
              <option v-for="c in joinedCourses" :key="c" :value="c">{{ c }}</option>
            </select>
          </label>
          <label>
            难度
            <select v-model="paperDifficulty" class="match-height">
              <option>基础</option>
              <option>中等</option>
              <option>拔高</option>
            </select>
          </label>
        </div>

        <label class="ui-block ui-mt-12">
          试卷标题（可选）
          <input v-model="paperTitle" class="match-height" placeholder="默认使用「课程名 试卷」" />
        </label>

        <div class="ui-mt-16 ui-overflow-x-auto">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width: 52px">#</th>
                <th style="min-width: 120px">题型</th>
                <th style="min-width: 200px">知识点</th>
                <th style="min-width: 88px">分值</th>
                <th style="min-width: 140px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in rows" :key="row.id">
                <td>{{ idx + 1 }}</td>
                <td>
                  <select
                    v-model="row.typeKey"
                    class="match-height"
                    style="width: 100%"
                    @change="row.question = null"
                  >
                    <option v-for="opt in TYPE_OPTIONS" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </option>
                  </select>
                </td>
                <td>
                  <select
                    v-model="row.pointName"
                    class="match-height"
                    style="width: 100%"
                    @change="row.question = null"
                  >
                    <option value="">— 选择知识点 —</option>
                    <option v-for="p in pointSelectOptions" :key="p" :value="p">{{ p }}</option>
                  </select>
                </td>
                <td>
                  <input
                    v-model.number="row.fullScore"
                    type="number"
                    min="1"
                    max="100"
                    class="match-height"
                    style="width: 100%; max-width: 88px"
                    title="本题满分，1–100"
                  />
                </td>
                <td>
                  <div class="spc-row-actions">
                    <button type="button" class="match-button" :disabled="row.loading" @click="generateRow(row)">
                      {{ row.loading ? '生成中…' : '生成本题' }}
                    </button>
                    <button type="button" class="cancel-button" :disabled="rows.length <= 1" @click="removeRow(idx)">
                      删除行
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <template v-for="(row, idx) in rows" :key="row.id + '-err'">
          <p v-if="row.rowError" class="error-text" style="margin-top: 6px">第 {{ idx + 1 }} 题：{{ row.rowError }}</p>
        </template>

        <div class="inline-form ui-mt-12">
          <button type="button" class="cancel-button" :disabled="rows.length >= 15" @click="addRow">增加一题</button>
          <button type="button" class="match-button" :disabled="saving || !previewRows.length" @click="persistPaper">
            {{ saving ? '保存中…' : '保存试卷并生成 MD' }}
          </button>
        </div>
        <p v-if="paperError" class="error-text" style="margin-top: 8px">{{ paperError }}</p>
      </article>

      <article v-if="previewRows.length" class="result-card">
        <h3>已生成题目预览</h3>
        <div v-for="(pr, idx) in previewRows" :key="pr.id + '-pv'" class="ui-mt-14">
          <h4>
            第 {{ idx + 1 }} 题（{{ pr.question?.question_type || '—' }} · {{ clampFullScore(pr.fullScore) }} 分）
          </h4>
          <div class="latex-block" v-html="renderLatexText(pr.question.question)"></div>
          <ul v-if="pr.question.options?.length" class="panel-subtitle" style="margin-top: 8px">
            <li v-for="(opt, j) in pr.question.options" :key="j" v-html="renderLatexText(opt)"></li>
          </ul>
        </div>
      </article>
    </template>
  </section>
</template>

<style src="./student-portal.css"></style>
