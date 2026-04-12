<script setup>
const props = defineProps({
  canShowGraphPage: { type: Boolean, required: true },
  isUnjoinedPreviewMode: { type: Boolean, required: true },
  graphData: { type: Object, required: true },
  graphLoading: { type: Boolean, required: true },
  graphError: { type: String, required: false, default: '' },
  graphNodeMastery: { type: Object, required: true },
  learningSuggestions: { type: Array, required: true },
  suggestionLoading: { type: Boolean, required: true },
  suggestionError: { type: String, required: false, default: '' },
  majorRelevance: { type: Object, required: true },
  relevanceLoading: { type: Boolean, required: true },
  relevanceError: { type: String, required: false, default: '' },
  relevanceLabel: { type: String, required: false, default: '' },
  materials: { type: Array, required: true },
  practiceTestAllowed: { type: Boolean, required: true }
})

const emit = defineEmits([
  'go-courses',
  'refresh-graph',
  'enter-test'
])

const goCourses = () => emit('go-courses')
const refreshGraph = () => emit('refresh-graph')
const enterTest = () => emit('enter-test')
</script>

<template>
  <section class="panel-stack">
    <article v-if="!canShowGraphPage" class="result-card">
      <h3 class="portal-section-title portal-section-title--cyan">请先选择课程</h3>
      <button type="button" class="match-button" @click="goCourses">去课程广场</button>
    </article>
    <template v-else>
      <article v-if="isUnjoinedPreviewMode" class="result-card" style="border-left:4px solid #f59e0b">
        <h3 class="portal-section-title portal-section-title--amber" style="margin-bottom: 0">浏览模式</h3>
      </article>
      <!-- 图谱画布与右侧信息仍由父组件提供 ref/内容，这里只负责上半部分布局 -->
      <slot />
    </template>
  </section>
</template>

<style>
@import './student-portal.css';
</style>
