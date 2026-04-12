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

onMounted(() => {
  window.addEventListener('resize', onResize)
  void loadStats()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <article class="result-card">
    <h3 class="portal-section-title portal-section-title--violet">学生分析</h3>

    <label class="ui-mt-12" style="display: block">
      统计知识点
      <select v-model="selectedPointName" class="match-height" style="width: 100%; box-sizing: border-box">
        <option v-for="p in points" :key="p.id" :value="p.pointName">{{ pointSelectLabel(p) }}</option>
      </select>
    </label>

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
        <h3 class="portal-subsection-title portal-subsection-title--amber">每题平均得分率</h3>
        <div ref="chartRef" class="teacher-analytics-chart"></div>
      </div>

      <div class="ui-mt-12">
        <h3 class="portal-subsection-title portal-subsection-title--rose">高得分题 / 低得分题</h3>
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
  </article>
</template>

<style scoped>
.teacher-analytics-chart {
  width: 100%;
  height: 320px;
  min-height: 320px;
}
</style>
