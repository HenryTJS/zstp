<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { http } from '../api/client'

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
const courseProgressLoading = ref(false)
const courseProgressError = ref('')
const courseProgress = ref({ students: [], totalResourceCount: 0, studentCount: 0 })

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

watch([() => props.selectedCourse, selectedPointName], () => {
  void loadStats()
})

const loadCourseProgress = async () => {
  if (!props.currentUser?.id || !props.selectedCourse) return
  courseProgressLoading.value = true
  courseProgressError.value = ''
  try {
    const { data } = await http.get('/resources/course-progress-overview', {
      params: {
        teacherUserId: props.currentUser.id,
        courseName: props.selectedCourse
      }
    })
    courseProgress.value = data || { students: [], totalResourceCount: 0, studentCount: 0 }
  } catch (e) {
    courseProgressError.value = e?.response?.data?.message || '课程总进度加载失败'
    courseProgress.value = { students: [], totalResourceCount: 0, studentCount: 0 }
  } finally {
    courseProgressLoading.value = false
  }
}

const chartRef = ref(null)
let chart = null
const progressChartRef = ref(null)
let progressChart = null

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

const renderProgressChart = async () => {
  await nextTick()
  if (!progressChartRef.value) return
  const list = Array.isArray(courseProgress.value?.students) ? courseProgress.value.students : []
  const x = list.length ? list.map((s) => s.username || s.workId || `U${s.userId}`) : ['—']
  const y = list.length ? list.map((s) => Number(s.percent || 0)) : [0]
  if (!progressChart) {
    progressChart = echarts.init(progressChartRef.value)
  }
  progressChart.setOption(
    {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: x,
        axisLabel: { interval: 0, rotate: x.length > 8 ? 35 : 0 }
      },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{ type: 'line', smooth: true, data: y, name: '课程进度(%)' }]
    },
    { notMerge: true }
  )
  progressChart.resize()
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

watch(
  () => courseProgress.value,
  async () => {
    await renderProgressChart()
  }
)

watch(
  () => props.selectedCourse,
  () => {
    void loadCourseProgress()
  }
)

const onResize = () => {
  chart?.resize()
  progressChart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  void loadStats()
  void loadCourseProgress()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  progressChart?.dispose()
  chart = null
  progressChart = null
})
</script>

<template>
  <article class="result-card">
    <h3>学生分析</h3>

    <label class="ui-mt-12" style="display: block">
      统计知识点
      <select v-model="selectedPointName" class="match-height" style="width: 100%; box-sizing: border-box">
        <option v-for="p in points" :key="p.id" :value="p.pointName">{{ pointSelectLabel(p) }}</option>
      </select>
    </label>

    <div class="ui-mt-12">
      <h3>课程总进度（按学生从高到低）</h3>
      <p v-if="courseProgressLoading" class="panel-subtitle">加载中…</p>
      <p v-else-if="courseProgressError" class="error-text">{{ courseProgressError }}</p>
      <template v-else>
        <p class="panel-subtitle">
          已加入学生：{{ courseProgress.studentCount || 0 }} 人；课程资源总数：{{ courseProgress.totalResourceCount || 0 }}
        </p>
        <div ref="progressChartRef" class="teacher-analytics-chart"></div>
      </template>
    </div>

    <p v-if="loading" class="panel-subtitle ui-mt-10">加载中…</p>
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
        <h3>每题平均得分率</h3>
        <p v-if="!(stats.perQuestionAvg && stats.perQuestionAvg.length)" class="panel-subtitle">
          暂无题目统计数据（可能未发布测试或无人提交）。
        </p>
        <div ref="chartRef" class="teacher-analytics-chart"></div>
      </div>

      <div class="ui-mt-12">
        <h3>高得分题 / 低得分题</h3>
        <div class="grid-form two-col">
          <div>
            <p class="panel-subtitle"><strong>高得分题</strong></p>
            <ul>
              <li v-for="r in stats.highScoreQuestions || []" :key="'h' + r.index">Q{{ r.index }}：{{ r.ratio }}%</li>
            </ul>
          </div>
          <div>
            <p class="panel-subtitle"><strong>低得分题</strong></p>
            <ul>
              <li v-for="r in stats.lowScoreQuestions || []" :key="'l' + r.index">Q{{ r.index }}：{{ r.ratio }}%</li>
            </ul>
          </div>
        </div>
      </div>
    </template>
  </article>
</template>

<style scoped>
.teacher-analytics-chart {
  width: 100%;
  height: 320px;
  min-height: 320px;
}
</style>
