<script setup>
import { computed, ref } from 'vue'
import { CourseDetailPanel } from '../../student/course'

const props = defineProps({
  courseDetail: { type: Object, default: null },
  courseDetailLoading: { type: Boolean, default: false },
  courseDetailError: { type: String, default: '' },
  teachersText: { type: String, default: '' },
  courseMetaSaving: { type: Boolean, default: false },
  courseMetaForm: { type: Object, required: true }
})

const emit = defineEmits(['update:course-meta-form', 'enter', 'quit', 'apply', 'save-meta', 'upload-cover'])
const metaEditorOpen = ref(false)
const defaultMdTemplate = computed(() => {
  const name = String(props.courseDetail?.courseName || '').trim() || '本课程'
  return `## 课程介绍

欢迎学习 **${name}**。本课程聚焦核心概念、案例分析与实践应用。

### 你将学到

- 核心知识框架
- 常见问题与解题思路
- 综合应用与复盘方法`
})
const isLegacyPlainDefaultSummary = (text) => {
  const s = String(text || '').trim()
  if (!s) return false
  const courseName = String(props.courseDetail?.courseName || '').trim()
  if (courseName && s === `${courseName}简介：围绕课程核心知识点，强调概念理解、案例分析与实践应用。`) return true
  return s.includes('简介：围绕课程核心知识点，强调概念理解、案例分析与实践应用。')
}
const openMetaEditor = () => {
  const current = String(props.courseMetaForm?.summary || '').trim()
  if (!current || isLegacyPlainDefaultSummary(current)) {
    emit('update:course-meta-form', {
      ...props.courseMetaForm,
      summary: defaultMdTemplate.value,
      syllabus: ''
    })
  }
  metaEditorOpen.value = true
}
</script>

<template>
  <div>
    <CourseDetailPanel
      role="teacher"
      :detail="courseDetail || { courseName: '', coverUrl: '', summary: '', syllabus: '' }"
      :loading="courseDetailLoading"
      :error="courseDetailError"
      :teachers-text="teachersText"
      :can-access="Boolean(courseDetail?.hasAccess)"
      :can-edit-meta="Boolean(courseDetail?.canEditMeta)"
      @enter="() => emit('enter')"
      @quit="() => emit('quit')"
      @apply="() => emit('apply')"
      @join="() => null"
      @upload-cover="(e) => emit('upload-cover', e)"
    />

    <div v-if="Boolean(courseDetail?.canEditMeta)" class="teacher-meta-actions">
      <button type="button" class="match-button" @click="openMetaEditor">课程信息编辑</button>
    </div>

    <div v-if="metaEditorOpen" class="meta-editor-mask" @click.self="metaEditorOpen = false">
      <section class="meta-editor-dialog" role="dialog" aria-modal="true" aria-label="编辑课程信息">
        <div class="meta-editor-head">
          <h3 class="portal-section-title portal-section-title--violet">编辑课程信息</h3>
          <button type="button" class="modal-close" aria-label="关闭" @click="metaEditorOpen = false">×</button>
        </div>

        <label class="ui-block ui-mt-8">
          <textarea
            :value="courseMetaForm.summary || ''"
            rows="16"
            class="detail-textarea detail-textarea--md"
            @input="emit('update:course-meta-form', { ...courseMetaForm, summary: $event.target.value, syllabus: '' })"
          ></textarea>
        </label>

        <div class="inline-form ui-mt-12">
          <button type="button" class="match-button" :disabled="courseMetaSaving" @click="emit('save-meta')">
            {{ courseMetaSaving ? '保存中...' : '保存课程信息' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.teacher-meta-actions{
  margin-top:12px;
}
.meta-editor-mask{
  position:fixed;
  inset:0;
  z-index:1200;
  background:rgba(15,23,42,.42);
  display:flex;
  align-items:center;
  justify-content:center;
  padding:16px;
}
.meta-editor-dialog{
  position: relative;
  width:min(860px,100%);
  max-height:calc(100vh - 32px);
  overflow:auto;
  background:#fff;
  border:1px solid var(--ui-card-border);
  border-radius:14px;
  box-shadow:0 16px 40px rgba(2,6,23,.24);
  padding:18px;
}
.meta-editor-head{
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:10px;
  margin-bottom:12px;
}
.meta-editor-head h3.portal-section-title{
  margin:0;
  padding-right:28px;
}
.detail-textarea {
  width: 100%;
  border-radius: 10px;
  border: 1px solid var(--ui-card-border);
  padding: 10px 12px;
  line-height: 1.6;
  resize: vertical;
}
.detail-textarea--md{
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
}
</style>

