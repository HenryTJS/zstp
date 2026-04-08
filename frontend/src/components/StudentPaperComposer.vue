<script setup>
import { computed, ref, watch } from 'vue'
import { fetchQuestion, listKnowledgePoints, saveExam } from '../api/client'

const props = defineProps({
  joinedCourses: { type: Array, required: true },
  currentCourse: { type: String, default: '' },
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

// =========================
// 知识点编号（章/节/知识点）
// 例如：1、1.1、1.1.1
// 用于组卷下拉“显示编号 + 名称”与排序；不影响后端数据结构
// =========================
const isCourseRootPoint = (item) => Boolean(item?.courseRoot)

/** 与列表接口一致：同级按 sortOrder 再按 id，避免「第10章」等顺序与编号错位 */
const cmpNodeOrder = (a, b) => {
  const oa = Number(a?.sortOrder ?? 0)
  const ob = Number(b?.sortOrder ?? 0)
  if (oa !== ob) return oa - ob

  const ta = a?.createdAt ? Date.parse(String(a.createdAt)) : NaN
  const tb = b?.createdAt ? Date.parse(String(b.createdAt)) : NaN
  const fa = Number.isFinite(ta)
  const fb = Number.isFinite(tb)
  if (fa && fb && ta !== tb) return ta - tb
  if (fa && !fb) return -1
  if (!fa && fb) return 1

  return Number(a?.id ?? 0) - Number(b?.id ?? 0)
}

const pointNumberMap = computed(() => {
  const outById = new Map() // key: String(id) -> "1.2.3"
  const list = Array.isArray(paperPointsRaw.value) ? paperPointsRaw.value : []
  if (!list.length) return outById

  const nodes = list.slice().sort(cmpNodeOrder)
  const childrenByParentId = new Map()
  const lastByTrimPointName = new Map() // 兼容旧数据：parentId 缺失时回退 parentPoint

  for (const p of nodes) {
    let parentId = p?.parentId == null ? null : Number(p.parentId)
    if (parentId == null) {
      const rawParent = p?.parentPoint == null ? null : String(p.parentPoint).trim()
      parentId = rawParent ? (lastByTrimPointName.get(rawParent)?.id ?? null) : null
    }
    if (!childrenByParentId.has(parentId)) childrenByParentId.set(parentId, [])
    childrenByParentId.get(parentId).push(p)

    const selfName = String(p?.pointName || '').trim()
    if (selfName) lastByTrimPointName.set(selfName, p)
  }

  const assign = (parentId, prefixParts, guard) => {
    const visiting = new Set(guard || [])
    if (prefixParts.length > 12) return
    if (parentId != null) {
      const key = String(parentId)
      if (visiting.has(key)) return
      visiting.add(key)
    }

    const children = (childrenByParentId.get(parentId) || []).slice().sort(cmpNodeOrder)
    let idx = 0
    for (const child of children) {
      if (isCourseRootPoint(child)) continue
      idx += 1
      const parts = [...prefixParts, idx]
      outById.set(String(child.id), parts.join('.'))
      assign(child.id, parts, visiting)
    }
  }

  // 顶层：parentId 为空的节点（且非课程根）作为“第 x 章”
  assign(null, [], new Set())

  // 兼容：若课程根作为真实父级的存储形态，也从课程根继续编号
  const courseRoot = nodes.find((p) => isCourseRootPoint(p))
  if (courseRoot?.id != null) {
    assign(courseRoot.id, [], new Set())
  }

  return outById
})

const getPointNumber = (pointOrName) => {
  if (pointOrName && typeof pointOrName === 'object') {
    return pointNumberMap.value.get(String(pointOrName.id)) || ''
  }
  const name = String(pointOrName || '').trim()
  if (!name) return ''
  const exact = (Array.isArray(paperPointsRaw.value) ? paperPointsRaw.value : []).find(
    (p) => String(p?.pointName || '').trim() === name
  )
  return exact ? pointNumberMap.value.get(String(exact.id)) || '' : ''
}

const paperCourse = ref('')
const paperDifficulty = ref('中等')
const paperTitle = ref('')
const paperPointsRaw = ref([])
const paperPointsLoading = ref(false)
const paperPointsError = ref('')
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
  () => [props.currentCourse, props.joinedCourses],
  ([cur, list]) => {
    const current = String(cur || '').trim()
    const arr = Array.isArray(list) ? list : []
    if (current && arr.includes(current)) {
      paperCourse.value = current
      return
    }
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
  paperPointsLoading.value = true
  paperPointsError.value = ''
  const cn = String(paperCourse.value || '').trim()
  if (!cn) {
    paperPointsRaw.value = []
    paperPointsLoading.value = false
    return
  }
  try {
    const { data } = await listKnowledgePoints(cn)
    const points = Array.isArray(data?.points) ? data.points : Array.isArray(data) ? data : []
    paperPointsRaw.value = points.slice().sort(sortPoints)
  } catch {
    // 不要静默失败，否则下拉框只剩“课程根知识点”，用户会以为系统没数据
    paperPointsRaw.value = []
    paperPointsError.value = '知识点列表加载失败，请稍后重试。'
  } finally {
    paperPointsLoading.value = false
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
  const list = Array.isArray(paperPointsRaw.value) ? paperPointsRaw.value : []

  // 根知识点（课程名）保留：用于“整门课”出题场景，但不参与 1/2/3 编号排序
  const rootOpt = cn ? [{ value: cn, label: cn, num: '' }] : []

  const items = list
    .filter((p) => p && !isCourseRootPoint(p))
    .map((p) => {
      const name = String(p?.pointName || '').trim()
      const num = getPointNumber(p) || ''
      return {
        value: name,
        label: num ? `${num} ${name}` : name,
        num
      }
    })
    .filter((x) => x.value)

  // 去重：同名取“编号更完整/更靠前”的一个
  const byName = new Map()
  for (const it of items) {
    const prev = byName.get(it.value)
    if (!prev) {
      byName.set(it.value, it)
      continue
    }
    // 优先保留有编号的；若都有编号，取编号更短者（更靠近上层）作为主显示
    if (!prev.num && it.num) byName.set(it.value, it)
    else if (prev.num && it.num && it.num.split('.').length < prev.num.split('.').length) byName.set(it.value, it)
  }

  const deduped = Array.from(byName.values()).sort((a, b) => {
    // 按编号数值排序（1.10 > 1.2 的数值问题用 numeric: true 解决）
    const na = String(a.num || a.value || '')
    const nb = String(b.num || b.value || '')
    const byNum = na.localeCompare(nb, undefined, { numeric: true })
    if (byNum !== 0) return byNum
    return String(a.value).localeCompare(String(b.value), 'zh-CN')
  })

  return [...rootOpt, ...deduped]
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

        <div class="grid-form two-col ui-mt-12">
          <label>
            课程
            <input
              :value="paperCourse || '未进入课程'"
              class="match-height"
              readonly
              disabled
              title="组卷课程与当前进入课程保持一致"
            />
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

        <div class="inline-form ui-mt-10">
          <button type="button" class="cancel-button" :disabled="paperPointsLoading" @click="loadPaperPoints">
            {{ paperPointsLoading ? '加载中…' : '刷新知识点' }}
          </button>
          <p v-if="paperPointsError" class="error-text" style="margin: 0">{{ paperPointsError }}</p>
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
                    <option v-for="p in pointSelectOptions" :key="p.value" :value="p.value">{{ p.label }}</option>
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
