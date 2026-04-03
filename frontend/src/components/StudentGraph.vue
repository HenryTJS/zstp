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
  practiceTestAllowed: { type: Boolean, required: true },
  practiceBatchAllowed: { type: Boolean, required: true }
})

const emit = defineEmits([
  'go-courses',
  'refresh-graph',
  'enter-test',
  'enter-exam'
])

const goCourses = () => emit('go-courses')
const refreshGraph = () => emit('refresh-graph')
const enterTest = () => emit('enter-test')
const enterExam = () => emit('enter-exam')
</script>

<template>
  <section class="panel-stack">
    <article v-if="!canShowGraphPage" class="result-card">
      <h3>请先选择课程</h3>
      <p class="panel-subtitle">请先到「课程广场」对某门课使用「进入课程」或「查看课程」，再浏览知识图谱。</p>
      <button type="button" class="match-button" @click="goCourses">去课程广场</button>
    </article>
    <template v-else>
      <article v-if="isUnjoinedPreviewMode" class="result-card" style="border-left:4px solid #f59e0b">
        <p class="panel-subtitle" style="margin:0">
          <strong>浏览模式：</strong>仅可查看图谱结构，点击节点不会加载资料、学习建议与关联度分析。加入该课程后可完整使用。
        </p>
      </article>
      <!-- 图谱画布与右侧信息仍由父组件提供 ref/内容，这里只负责上半部分布局 -->
      <slot />
    </template>
  </section>
</template>

<style src="./student-portal.css"></style>
