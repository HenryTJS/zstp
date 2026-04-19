<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import MarkdownIt from 'markdown-it'
import { getKnowledgePointPublishedTestLearningReport, http } from '../../api/client'
import { renderLatexText } from '../../shared/utils/renderLatexHtml'

const props = defineProps({
  currentUser: { type: Object, required: true },
  selectedCourse: { type: String, default: '' },
  points: { type: Array, default: () => [] },
  pointName: { type: String, default: '' },
  /** 与知识点表格一致的编号，如 "1.2" */
  getPointNumber: { type: Function, default: () => '' }
})

const emit = defineEmits(['update:pointName'])

const selectedPointName = computed({
  get: () => String(props.pointName || ''),
  set: (v) => emit('update:pointName', String(v || ''))
})

watch(
  () => props.points,
  (list) => {
    if (selectedPointName.value) return
    const arr = Array.isArray(list) ? list : []
    const root = arr.find((p) => p?.courseRoot)
    if (!selectedPointName.value) {
      selectedPointName.value = root?.pointName || arr[0]?.pointName || ''
    }
  },
  { immediate: true }
)

const pointSelectLabel = (p) => {
  if (!p) return ''
  const num = props.getPointNumber ? String(props.getPointNumber(p) || '').trim() : ''
  const name = String(p.pointName || '').trim()
  return num ? `${num} ${name}` : name
}

const loading = ref(false)
const error = ref('')
const stats = ref(null)

const reportLoading = ref(false)
const reportError = ref('')
const reportData = ref(null)

/** 仅用于 AI 建议等纯 Markdown 段落（与练习页一致：公式走 renderLatexText，不走 markdown-it-katex） */
const mdLite = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: true
})

const teachingSuggestionsHtml = computed(() => {
  const raw = String(reportData.value?.teachingSuggestions || '').trim()
  if (!raw) return ''
  return mdLite.render(raw)
})

/** 无试卷等兜底：整段仍为 Markdown 时用轻量渲染 */
const fallbackReportHtml = computed(() => {
  const raw = String(reportData.value?.reportMarkdown || '').trim()
  if (!raw) return ''
  return mdLite.render(raw)
})

const hasStructuredReport = computed(() => {
  const d = reportData.value
  return Boolean(d?.testId && Array.isArray(d.questions) && d.questions.length > 0)
})

const formatWrongDist = (dist) => {
  if (!Array.isArray(dist) || !dist.length) return '—'
  return dist.map((d) => `${d.option}:${d.count}`).join('，')
}

const canLoad = computed(() => Boolean(props.currentUser?.id && props.selectedCourse && selectedPointName.value))

const loadStats = async () => {
  if (!canLoad.value) return
  loading.value = true
  error.value = ''
  stats.value = null
  try {
    const { data } = await http.get('/knowledge-point-published-tests/stats', {
      params: {
        teacherUserId: props.currentUser.id,
        courseName: props.selectedCourse,
        pointName: selectedPointName.value
      }
    })
    stats.value = data || null
  } catch (e) {
    error.value = e?.response?.data?.message || '加载统计失败'
    stats.value = null
  } finally {
    loading.value = false
  }
}

const loadLearningReport = async () => {
  if (!canLoad.value) return
  reportLoading.value = true
  reportError.value = ''
  reportData.value = null
  try {
    const { data } = await getKnowledgePointPublishedTestLearningReport({
      teacherUserId: props.currentUser.id,
      courseName: props.selectedCourse,
      pointName: selectedPointName.value
    })
    reportData.value = data || null
  } catch (e) {
    reportError.value = e?.response?.data?.message || '生成学情报告失败'
    reportData.value = null
  } finally {
    reportLoading.value = false
  }
}

const refreshAll = async () => {
  reportData.value = null
  await loadStats()
}

watch([() => props.selectedCourse, selectedPointName], () => {
  reportData.value = null
  void refreshAll()
})

const chartRef = ref(null)
let chart = null

const renderChart = async () => {
  await nextTick()
  if (!chartRef.value || !stats.value) return

  const per = Array.isArray(stats.value?.perQuestionAvg) ? stats.value.perQuestionAvg : []
  const x = per.length ? per.map((r) => `Q${r.index}`) : ['—']
  const y = per.length ? per.map((r) => Number(r.ratio || 0)) : [0]

  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  chart.setOption(
    {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: x },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{ type: 'bar', data: y, name: '平均得分率(%)' }]
    },
    { notMerge: true }
  )
  chart.resize()
}

watch(
  () => stats.value,
  async (s) => {
    if (!s) {
      chart?.dispose()
      chart = null
      return
    }
    await nextTick()
    await renderChart()
  }
)

const onResize = () => {
  chart?.resize()
}

const sanitizeFilePart = (s) =>
  String(s || '')
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, '_')
    .slice(0, 96)

const exportReportMd = () => {
  const body = String(reportData.value?.reportMarkdown || '').trim()
  if (!body) return
  const course = sanitizeFilePart(props.selectedCourse)
  const point = sanitizeFilePart(selectedPointName.value)
  const stamp = sanitizeFilePart(reportData.value?.generatedAt || new Date().toISOString().slice(0, 19))
  const name = `学情报告_${course}_${point}_${stamp}.md`
  const blob = new Blob([body], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

const showReportPreview = computed(() => {
  if (!reportData.value) return false
  if (hasStructuredReport.value) return true
  return Boolean(String(reportData.value?.reportMarkdown || '').trim())
})

onMounted(() => {
  window.addEventListener('resize', onResize)
  void refreshAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="teacher-analytics-body">
    <h3 class="portal-section-title portal-section-title--violet">学生分析</h3>

    <label v-if="(points || []).length" class="block-label ui-mt-10">
      知识点
      <select v-model="selectedPointName" class="match-height" style="width: 100%; max-width: 420px; box-sizing: border-box">
        <option v-for="p in points" :key="String(p.pointName)" :value="String(p.pointName || '')">
          {{ pointSelectLabel(p) }}
        </option>
      </select>
    </label>

    <p v-if="loading" class="panel-subtitle ui-mt-10">加载统计中…</p>
    <p v-else-if="error" class="error-text ui-mt-10">{{ error }}</p>

    <template v-else-if="stats">
      <div class="profile-stat-list ui-mt-12">
        <div>
          <span>完成率</span>
          <strong>{{ stats.completionRate || 0 }}%</strong>
        </div>
        <div>
          <span>最高分</span>
          <strong>{{ stats.max || 0 }}</strong>
        </div>
        <div>
          <span>最低分</span>
          <strong>{{ stats.min || 0 }}</strong>
        </div>
        <div>
          <span>平均分</span>
          <strong>{{ stats.avg || 0 }}</strong>
        </div>
      </div>

      <div class="ui-mt-12">
        <h3 class="portal-subsection-title portal-subsection-title--amber">每题平均得分率</h3>
        <div ref="chartRef" class="teacher-analytics-chart"></div>
      </div>

      <div class="ui-mt-12">
        <div class="grid-form two-col">
          <div>
            <h4 class="portal-subsection-title portal-subsection-title--violet">高得分题</h4>
            <ul>
              <li v-for="r in stats.highScoreQuestions || []" :key="'h' + r.index">Q{{ r.index }}：{{ r.ratio }}%</li>
            </ul>
          </div>
          <div>
            <h4 class="portal-subsection-title portal-subsection-title--teal">低得分题</h4>
            <ul>
              <li v-for="r in stats.lowScoreQuestions || []" :key="'l' + r.index">Q{{ r.index }}：{{ r.ratio }}%</li>
            </ul>
          </div>
        </div>
      </div>
    </template>

    <div class="ui-mt-16 analytics-divider">
      <h3 class="portal-subsection-title portal-subsection-title--amber">学情分析报告</h3>
      <div class="inline-form ui-mt-10">
        <button type="button" class="match-button" :disabled="reportLoading || !canLoad" @click="loadLearningReport">
          {{ reportLoading ? '生成中…' : reportData ? '重新生成报告' : '生成学情报告' }}
        </button>
        <button type="button" class="cancel-button" :disabled="!reportData?.reportMarkdown" @click="exportReportMd">
          导出 Markdown
        </button>
      </div>
      <p v-if="reportError" class="error-text ui-mt-8">{{ reportError }}</p>

      <!-- 结构化预览：与 StudentExercise 相同 renderLatexText，避免 markdown-it 内公式/块级公式失效 -->
      <article
        v-if="showReportPreview && hasStructuredReport"
        class="report-md-preview teacher-report-structured ui-mt-12"
        aria-label="学情报告预览"
      >
        <h1 class="teacher-report-h1">学情分析报告</h1>
        <ul class="teacher-report-meta">
          <li><strong>课程</strong>：{{ reportData.courseName }}</li>
          <li><strong>知识点锚点</strong>：{{ reportData.pointName }}</li>
          <li><strong>试卷标题</strong>：{{ reportData.title }}</li>
          <li><strong>已提交份数</strong>：{{ reportData.submissionCount ?? 0 }}</li>
          <li v-if="reportData.completionRatePercent != null">
            <strong>完成率</strong>：{{ reportData.completionRatePercent }}%
          </li>
        </ul>

        <h2 class="teacher-report-h2">班级成绩概览</h2>
        <template v-if="!reportData.submissionCount">
          <p class="panel-subtitle">暂无学生提交答卷。</p>
        </template>
        <ul v-else-if="reportData.classScoreOverview" class="teacher-report-meta">
          <li><strong>最高分</strong>：{{ reportData.classScoreOverview.maxScore }}</li>
          <li><strong>最低分</strong>：{{ reportData.classScoreOverview.minScore }}</li>
          <li><strong>平均分</strong>：{{ reportData.classScoreOverview.avgScore }}</li>
        </ul>

        <h2 class="teacher-report-h2">AI 教学建议</h2>
        <div
          v-if="teachingSuggestionsHtml"
          class="teacher-report-ai-md"
          v-html="teachingSuggestionsHtml"
        ></div>
        <p v-else class="panel-subtitle">（无）</p>

        <h2 class="teacher-report-h2">逐题分析</h2>
        <section v-for="q in reportData.questions" :key="'q' + q.index" class="teacher-report-q-card">
          <h3 class="teacher-report-h3">第 {{ q.index }} 题（{{ q.question_type }}）</h3>
          <p class="teacher-report-line">
            <strong>得分率</strong>：
            <template v-if="q.scoreRatePercent == null">—（尚无答卷）</template>
            <template v-else>{{ q.scoreRatePercent }}%（基于 {{ q.answeredCount }} 份有效作答）</template>
          </p>

          <p class="teacher-report-label">题干</p>
          <div class="latex-block teacher-report-latex" v-html="renderLatexText(q.question)"></div>

          <p class="teacher-report-label">参考答案</p>
          <div class="latex-block teacher-report-latex teacher-report-latex--inline" v-html="renderLatexText(String(q.answer ?? ''))"></div>

          <p class="teacher-report-label">解析</p>
          <div class="latex-block teacher-report-latex" v-html="renderLatexText(q.explanation)"></div>

          <template v-if="q.question_type === '选择题'">
            <p class="teacher-report-line">
              <strong>高频错选（在答错样本中）</strong>：
              <template v-if="!q.topWrongChoiceCount">无</template>
              <template v-else>{{ q.topWrongChoice }}（{{ q.topWrongChoiceCount }} 人次）；错选分布：{{ formatWrongDist(q.wrongChoiceDistribution) }}</template>
            </p>
          </template>

          <p class="teacher-report-line">
            <strong>未得满分学生</strong>：
            <template v-if="!q.wrongStudentNames?.length"><em>无</em></template>
            <template v-else>{{ q.wrongStudentNames.join('、') }}</template>
          </p>
        </section>
      </article>

      <!-- 无已发布测试等：仅 Markdown 兜底 -->
      <article
        v-else-if="showReportPreview && fallbackReportHtml"
        class="report-md-preview teacher-report-fallback-md ui-mt-12"
        aria-label="学情报告预览"
        v-html="fallbackReportHtml"
      ></article>
    </div>
  </div>
</template>

<style scoped>
.profile-stat-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.teacher-analytics-chart {
  width: 100%;
  height: 260px;
  min-height: 200px;
}

.block-label {
  display: block;
  font-size: 0.9rem;
}

.analytics-divider {
  padding-top: 14px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

/* 高度随内容伸缩，由外层 modal-container 统一滚动，避免内层再占一截“空白滚动区” */
.report-md-preview {
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 10px;
  padding: 16px 18px;
  overflow-x: hidden;
  background: #fafbff;
  color: #334155;
  font-size: 15px;
  line-height: 1.65;
}

.teacher-report-h1 {
  margin: 0 0 0.75em;
  font-size: 1.35rem;
  font-weight: 700;
  color: #0f172a;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  padding-bottom: 0.35em;
}

.teacher-report-h2 {
  margin: 1.1em 0 0.5em;
  font-size: 1.15rem;
  font-weight: 700;
  color: #0f172a;
}

.teacher-report-h2:first-of-type {
  margin-top: 0.5em;
}

.teacher-report-h3 {
  margin: 0 0 0.5em;
  font-size: 1.05rem;
  font-weight: 700;
  color: #1e1b4b;
}

.teacher-report-meta {
  margin: 0 0 1em;
  padding-left: 1.25em;
}

.teacher-report-meta li {
  margin: 0.2em 0;
}

.teacher-report-q-card {
  margin-top: 1.25em;
  padding-top: 1em;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.teacher-report-q-card:first-of-type {
  border-top: none;
  padding-top: 0;
  margin-top: 0.5em;
}

.teacher-report-label {
  margin: 0.65em 0 0.35em;
  font-weight: 600;
  color: #0f172a;
  font-size: 0.95rem;
}

.teacher-report-line {
  margin: 0.35em 0;
  line-height: 1.6;
}

/* 与练习页一致：题内不单独出现横向/纵向滚动条 */
.teacher-report-latex {
  overflow: visible;
  max-height: none;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(99, 102, 241, 0.06);
  border: 1px solid rgba(99, 102, 241, 0.12);
}

.teacher-report-latex--inline {
  display: inline-block;
  width: 100%;
  box-sizing: border-box;
  vertical-align: top;
}

.teacher-report-ai-md :deep(h1),
.teacher-report-ai-md :deep(h2),
.teacher-report-ai-md :deep(h3) {
  margin: 0.65em 0 0.35em;
  font-size: 1.05rem;
  color: #0f172a;
}

.teacher-report-ai-md :deep(p) {
  margin: 0 0 0.65em;
}

.teacher-report-ai-md :deep(ul) {
  margin: 0 0 0.65em;
  padding-left: 1.25em;
}

.teacher-report-fallback-md :deep(h1) {
  margin-top: 0;
  font-size: 1.25rem;
}

@media (max-width: 760px) {
  .profile-stat-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
