<script setup>
import { computed, nextTick, watch } from 'vue'
import StudentBranchGraph from './StudentBranchGraph.vue'
import { useStudentForceGraph } from '../../composables/useStudentForceGraph'

const props = defineProps({
  title: { type: String, default: '知识图谱' },
  view: { type: String, default: 'force' },
  graphLoading: { type: Boolean, required: true },
  graphError: { type: String, default: '' },
  graphData: { type: Object, required: true },
  graphNetworkData: { type: Object, default: null },
  isUnjoinedPreviewMode: { type: Boolean, required: true }
})

const emit = defineEmits(['refresh', 'node-click', 'update:view'])

const view = computed({
  get() {
    return props.view || 'force'
  },
  set(v) {
    emit('update:view', v)
  }
})

const enabledRef = computed(() => view.value === 'force')
const disabledRef = computed(() => props.isUnjoinedPreviewMode)
const graphNetworkDataRef = computed(() => props.graphNetworkData)

const { graphChartRef, render, resize, dispose } = useStudentForceGraph({
  enabledRef,
  disabledRef,
  graphNetworkDataRef,
  onNodeClick: (payload) => emit('node-click', payload)
})

const refresh = () => emit('refresh')
const toggleView = () => {
  view.value = view.value === 'force' ? 'branch' : 'force'
}

watch(
  () => view.value,
  async (v) => {
    if (v === 'force') {
      await nextTick()
      render()
      resize()
    } else {
      dispose()
    }
  },
  { immediate: true }
)
</script>

<template>
  <article class="result-card">
    <h3 class="portal-section-title portal-section-title--violet">{{ graphData.title || title }}</h3>
    <div class="inline-form">
      <button type="button" class="match-button" :disabled="graphLoading" @click="refresh">
        {{ graphLoading ? '加载中...' : '刷新图谱' }}
      </button>
      <button
        type="button"
        class="nav-btn"
        :disabled="graphLoading || !graphData.nodes?.length"
        @click="toggleView"
      >
        切换视图：{{ view === 'force' ? '力导向图' : '分支图' }}
      </button>
    </div>
    <p v-if="graphError" class="error-text">{{ graphError }}</p>

    <div v-if="view === 'force'" ref="graphChartRef" style="width: 100%; height: 420px;"></div>
    <StudentBranchGraph
      v-else
      :graph-data="graphData"
      :disabled="isUnjoinedPreviewMode"
      @node-click="(p) => emit('node-click', { ...p, category: null })"
    />
  </article>
</template>

<style>
@import '@/student/styles/student-portal.css';
</style>

