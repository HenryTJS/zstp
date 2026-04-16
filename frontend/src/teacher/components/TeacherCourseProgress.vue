<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { http } from '../../api/client'

const props = defineProps({
  currentUser: { type: Object, required: true },
  selectedCourse: { type: String, default: '' }
})

const courseProgressLoading = ref(false)
const courseProgressError = ref('')
const courseProgress = ref({ students: [], totalResourceCount: 0, studentCount: 0 })

const progressChartRef = ref(null)
let progressChart = null

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
  progressChart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  void loadCourseProgress()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  progressChart?.dispose()
  progressChart = null
})
</script>

<template>
  <article class="result-card">
    <h3 class="portal-section-title portal-section-title--cyan">课程总进度分析</h3>
    <p v-if="courseProgressLoading" class="panel-subtitle ui-mt-10">加载中…</p>
    <p v-else-if="courseProgressError" class="error-text ui-mt-10">{{ courseProgressError }}</p>
    <template v-else>
      <div ref="progressChartRef" class="teacher-course-progress-chart ui-mt-10"></div>
    </template>
  </article>
</template>

<style scoped>
.teacher-course-progress-chart {
  width: 100%;
  height: 320px;
  min-height: 320px;
}
</style>
