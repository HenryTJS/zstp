<script setup>
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  userInitial: { type: String, required: true },
  profileForm: { type: Object, required: true },
  currentUser: { type: Object, required: true },
  selectedCourse: { type: String, required: true },
  joinedCourses: { type: Array, required: true },
  selectedMajorDisplay: { type: String, required: true },
  learningStats: { type: Object, required: true },
  dimensionScores: { type: Object, required: false, default: null },
  dimensionScoresLoading: { type: Boolean, required: false, default: false },
  dimensionScoresError: { type: String, required: false, default: '' },
  filteredWrongBookCount: { type: Number, required: true },
  profileMessage: { type: String, required: false, default: '' }
})

const emit = defineEmits([
  'update:selectedCourse',
  'edit-profile',
  'change-password',
  'logout'
])

const radarRef = ref(null)
let radarChart = null

const buildRadarOption = () => {
  const dim = props.dimensionScores?.dimensionScores || {}
  const values = [
    Number(dim.logicReasoning || 0),
    Number(dim.numericCalculation || 0),
    Number(dim.semanticUnderstanding || 0),
    Number(dim.spatialImagination || 0),
    Number(dim.memoryRetrieval || 0)
  ]
  return {
    tooltip: { trigger: 'item' },
    radar: {
      indicator: [
        { name: '逻辑推理', max: 100 },
        { name: '数量计算', max: 100 },
        { name: '语义理解', max: 100 },
        { name: '空间想象', max: 100 },
        { name: '记忆检索', max: 100 }
      ],
      radius: '60%',
      splitNumber: 4
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: values,
            name: '能力维度',
            areaStyle: { opacity: 0.18 }
          }
        ]
      }
    ]
  }
}

const renderRadar = () => {
  if (!radarRef.value) return
  // 若 DOM 被替换（例如条件渲染导致），需重建实例
  if (radarChart && radarChart.getDom && radarChart.getDom() !== radarRef.value) {
    radarChart.dispose()
    radarChart = null
  }
  if (!radarChart) {
    radarChart = echarts.init(radarRef.value)
  }
  radarChart.setOption(buildRadarOption(), true)
  radarChart.resize()
}

onMounted(async () => {
  await nextTick()
  renderRadar()
  window.addEventListener('resize', renderRadar)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', renderRadar)
  if (radarChart) {
    radarChart.dispose()
    radarChart = null
  }
})

watch(
  () => props.dimensionScores,
  async () => {
    await nextTick()
    renderRadar()
  },
  { deep: true }
)
</script>

<template>
  <section class="panel-stack">
    <article class="result-card profile-hero-card profile-hero-card--no-actions">
      <div class="profile-hero-main">
        <div class="profile-avatar">{{ userInitial }}</div>
        <div>
          <h3>{{ profileForm.username || currentUser.username }}</h3>
        </div>
      </div>
    </article>

    <div class="profile-grid">
      <article class="result-card profile-overview-card">
        <h3 class="portal-section-title">学习画像</h3>
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
            <strong>{{ filteredWrongBookCount }}</strong>
          </div>
        </div>

        <div class="ui-mt-12">
          <h3 class="portal-section-title portal-section-title--violet">五维能力雷达图</h3>
          <p v-show="dimensionScoresLoading" class="panel-subtitle">计算中…</p>
          <p v-show="!dimensionScoresLoading && !!dimensionScoresError" class="error-text">{{ dimensionScoresError }}</p>
          <div
            ref="radarRef"
            class="student-radar-box"
            v-show="!dimensionScoresLoading && !dimensionScoresError && !!dimensionScores?.usedCourses?.length"
          ></div>
        </div>
      </article>

      <article class="result-card profile-detail-card">
        <h3 class="portal-section-title portal-section-title--teal">资料设置</h3>
        <div class="grid-form">
          <label>
            用户名
            <div class="panel-subtitle">{{ profileForm.username || currentUser.username }}</div>
          </label>
          <label>
            学工号
            <div class="panel-subtitle">{{ currentUser.workId || '未设置' }}</div>
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
          <button type="button" class="nav-btn" @click="emit('edit-profile')">编辑资料</button>
          <button type="button" class="nav-btn" @click="emit('change-password')">修改密码</button>
          <button type="button" class="danger-btn profile-logout-btn" @click="emit('logout')">退出登录</button>
        </div>
        <p v-if="profileMessage" class="ok-text">{{ profileMessage }}</p>
      </article>
    </div>
  </section>
</template>

<style>
@import '@/styles/student/student-portal.css';
</style>

