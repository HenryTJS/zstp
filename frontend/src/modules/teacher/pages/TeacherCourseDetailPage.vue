<script setup>
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
</script>

<template>
  <CourseDetailPanel
    role="teacher"
    :detail="courseDetail || { courseName: '', coverUrl: '', summary: '', syllabus: '' }"
    :loading="courseDetailLoading"
    :error="courseDetailError"
    :teachers-text="teachersText"
    :can-access="Boolean(courseDetail?.hasAccess)"
    :can-edit-meta="Boolean(courseDetail?.canEditMeta)"
    :is-submitting="courseMetaSaving"
    :edit-form="courseMetaForm"
    @update:edit-form="(v) => emit('update:course-meta-form', v)"
    @enter="() => emit('enter')"
    @quit="() => emit('quit')"
    @apply="() => emit('apply')"
    @join="() => null"
    @save-meta="() => emit('save-meta')"
    @upload-cover="(e) => emit('upload-cover', e)"
  />
</template>

