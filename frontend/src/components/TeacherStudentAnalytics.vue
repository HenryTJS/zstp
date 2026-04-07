<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { http } from '../api/client'

const props = defineProps({
  currentUser: { type: Object, required: true },
  // 可访问课程列表（TeacherPortal 已拉取权限）
  courseOptions: { type: Array, required: true },
  // 当前“进入课程”
  selectedCourse: { type: String, default: '' },
  points: { type: Array, default: () => [] },
  pointName: { type: String, default: '' }
})

const emit = defineEmits(['update:pointName'])

const selectedPointName = computed({
  get: () => String(props.pointName || ''),
  set: (v) => emit('update:pointName', String(v || ''))
})

watch(
  () => props.points,
  (list) => {
    // 默认选中课程根/或第一个点
    if (selectedPointName.value) return
    const arr = Array.isArray(list) ? list : []
    const root = arr.find((p) => p?.courseRoot)
    if (!selectedPointName.value) {
      selectedPointName.value = root?.pointName || arr[0]?.pointName || ''
    }
  },
  { immediate: true }
)

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
    await renderChart()
  }
}

watch([() => props.selectedCourse, selectedPointName], () => {
  void loadStats()
})

const chartRef = ref(null)
let chart = null

const renderChart = async () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const per = Array.isArray(stats.value?.perQuestionAvg) ? stats.value.perQuestionAvg : []
  const x = per.map((r) => `Q${r.index}`)
  const y = per.map((r) => Number(r.ratio || 0))
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: x },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
    series: [{ type: 'bar', data: y, name: '平均得分率(%)' }]
  })
}

onMounted(() => {
  window.addEventListener('resize', () => chart?.resize())
  void loadStats()
})
</script>

<template>
  <article class="result-card">
    <h3>学生分析</h3>

    <div class="grid-form two-col ui-mt-12">
      <label>
        课程（进入课程后生效）
        <div class="panel-subtitle">{{ selectedCourse || '未进入课程' }}</div>
      </label>
      <label>
        统计知识点（教师发布测试）
        <select v-model="selectedPointName" class="match-height" style="width: 100%">
          <option v-for="p in points" :key="p.id" :value="p.pointName">{{ p.pointName }}</option>
        </select>
      </label>
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
        <div ref="chartRef" style="width:100%;height:320px"></div>
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

<style src="./teacher-portal.css"></style>

