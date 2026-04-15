<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { http } from '../../../api/client'

const props = defineProps({
  currentUser: { type: Object, required: true },
  selectedCourse: { type: String, default: '' },
  points: { type: Array, default: () => [] },
  /** 当前课程已加载的资料条数（与资源管理列表一致） */
  materialCount: { type: Number, default: 0 }
})

const loading = ref(false)
const error = ref('')
const summary = ref(null)
const progressOverview = ref({
  students: [],
  totalResourceCount: 0,
  studentCount: 0
})

const knowledgePointCount = computed(() => {
  const arr = Array.isArray(props.points) ? props.points : []
  return arr.filter((p) => p && !p.courseRoot).length
})

const avgResourcePercent = computed(() => {
  const list = Array.isArray(progressOverview.value?.students) ? progressOverview.value.students : []
  if (!list.length) return 0
  const sum = list.reduce((acc, s) => acc + Number(s.percent || 0), 0)
  return Math.round((sum / list.length) * 10) / 10
})

/** 大块彩色 KPI：theme 对应下方 CSS 修饰类 */
const kpiTiles = computed(() => {
  const s = summary.value
  const p = progressOverview.value
  return [
    { theme: 'kpi--students', label: '在读学生', value: String(p?.studentCount ?? 0), unit: '人' },
    { theme: 'kpi--points', label: '知识点（非根）', value: String(knowledgePointCount.value), unit: '个' },
    { theme: 'kpi--materials', label: '资料篇数', value: String(props.materialCount ?? 0), unit: '篇' },
    { theme: 'kpi--resources', label: '课程资源项', value: String(p?.totalResourceCount ?? 0), unit: '项' },
    { theme: 'kpi--progress', label: '平均完成度', value: String(avgResourcePercent.value), unit: '%' },
    { theme: 'kpi--tests', label: '已发布测试', value: String(s?.publishedTestCount ?? 0), unit: '套' },
    { theme: 'kpi--submits', label: '测试提交人次', value: String(s?.testSubmissionCount ?? 0), unit: '次' },
    { theme: 'kpi--participants', label: '参与测试学生', value: String(s?.distinctSubmitters ?? 0), unit: '人' },
    { theme: 'kpi--avgscore', label: '测试均分（全课）', value: String(s?.courseAvgScore ?? 0), unit: '分' }
  ]
})

const loadAll = async () => {
  if (!props.currentUser?.id || !props.selectedCourse) {
    summary.value = null
    progressOverview.value = { students: [], totalResourceCount: 0, studentCount: 0 }
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [sumRes, progRes] = await Promise.all([
      http.get('/knowledge-point-published-tests/course-summary', {
        params: { teacherUserId: props.currentUser.id, courseName: props.selectedCourse }
      }),
      http.get('/resources/course-progress-overview', {
        params: { teacherUserId: props.currentUser.id, courseName: props.selectedCourse }
      })
    ])
    summary.value = sumRes?.data || null
    progressOverview.value = progRes?.data || { students: [], totalResourceCount: 0, studentCount: 0 }
  } catch (e) {
    error.value = e?.response?.data?.message || '数据看板加载失败'
    summary.value = null
    progressOverview.value = { students: [], totalResourceCount: 0, studentCount: 0 }
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.selectedCourse, props.currentUser?.id],
  () => {
    void loadAll()
  }
)

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <article class="result-card teacher-course-dashboard">
    <h3 class="portal-section-title">课程数据看板</h3>

    <p v-if="loading" class="panel-subtitle ui-mt-10">加载中…</p>
    <p v-else-if="error" class="error-text ui-mt-10">{{ error }}</p>

    <template v-else>
      <div class="kpi-grid ui-mt-12">
        <div v-for="(tile, i) in kpiTiles" :key="i" class="kpi-tile" :class="tile.theme">
          <p class="kpi-label">{{ tile.label }}</p>
          <p class="kpi-value">
            <span class="kpi-num">{{ tile.value }}</span>
            <span v-if="tile.unit" class="kpi-unit">{{ tile.unit }}</span>
          </p>
        </div>
      </div>

      <div class="ui-mt-12">
        <h3 class="portal-subsection-title portal-subsection-title--teal">各知识点已发布测试</h3>
        <div v-if="summary?.tests && summary.tests.length" class="dashboard-table-wrap">
          <table class="dashboard-table">
            <thead>
              <tr>
                <th>知识点</th>
                <th>标题</th>
                <th>提交数</th>
                <th>完成率</th>
                <th>平均分</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in summary.tests" :key="row.testId">
                <td>{{ row.pointName || '—' }}</td>
                <td>{{ row.title || '—' }}</td>
                <td>{{ row.submissions ?? 0 }}</td>
                <td>{{ row.completionRate ?? 0 }}%</td>
                <td>{{ row.avgScore ?? 0 }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </article>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 14px;
}

.kpi-tile {
  border-radius: 14px;
  padding: 18px 16px 20px;
  min-height: 112px;
  box-sizing: border-box;
  color: #fff;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.12);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 6px;
  position: relative;
  overflow: hidden;
}

.kpi-tile::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.18) 0%, transparent 45%);
  pointer-events: none;
}

.kpi-label {
  margin: 0;
  font-size: 0.8125rem;
  font-weight: 500;
  opacity: 0.92;
  line-height: 1.35;
  position: relative;
  z-index: 1;
}

.kpi-value {
  margin: 0;
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 6px;
  position: relative;
  z-index: 1;
}

.kpi-num {
  font-size: clamp(1.75rem, 4.5vw, 2.35rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.kpi-unit {
  font-size: 0.9rem;
  font-weight: 600;
  opacity: 0.88;
}

.kpi-footnote {
  opacity: 0.78;
}

/* 各色块：斜向渐变 + 饱和主色，保证白字对比度 */
.kpi--students {
  background: linear-gradient(145deg, #4f46e5 0%, #6366f1 48%, #4338ca 100%);
}

.kpi--points {
  background: linear-gradient(145deg, #7c3aed 0%, #8b5cf6 50%, #6d28d9 100%);
}

.kpi--materials {
  background: linear-gradient(145deg, #0d9488 0%, #14b8a6 50%, #0f766e 100%);
}

.kpi--resources {
  background: linear-gradient(145deg, #0284c7 0%, #0ea5e9 52%, #0369a1 100%);
}

.kpi--progress {
  background: linear-gradient(145deg, #059669 0%, #10b981 50%, #047857 100%);
}

.kpi--tests {
  background: linear-gradient(145deg, #d97706 0%, #f59e0b 52%, #b45309 100%);
}

.kpi--submits {
  background: linear-gradient(145deg, #ea580c 0%, #f97316 50%, #c2410c 100%);
}

.kpi--participants {
  background: linear-gradient(145deg, #db2777 0%, #ec4899 52%, #be185d 100%);
}

.kpi--avgscore {
  background: linear-gradient(145deg, #2563eb 0%, #3b82f6 50%, #1d4ed8 100%);
}

.dashboard-table-wrap {
  overflow-x: auto;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 8px;
}

.dashboard-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.dashboard-table th,
.dashboard-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.dashboard-table th {
  font-weight: 600;
  background: rgba(0, 0, 0, 0.03);
}

.dashboard-table tbody tr:last-child td {
  border-bottom: none;
}
</style>
