<script setup>
import TeacherCourses from '../components/TeacherCourses.vue'

defineProps({
  myCourseCatalog: { type: Array, required: true },
  pendingPermissionRequests: { type: Array, default: () => [] },
  permissionRequestsError: { type: String, default: '' },
  courseInitDone: { type: Boolean, default: false },
  teachersByCourse: { type: Object, default: () => ({}) },
  teachersLoading: { type: Boolean, default: false }
})

const emit = defineEmits(['enter-course', 'apply-new-course'])
</script>

<template>
  <TeacherCourses
    :my-course-catalog="myCourseCatalog"
    :pending-permission-requests="pendingPermissionRequests"
    :permission-requests-error="permissionRequestsError"
    :course-init-done="courseInitDone"
    :teachers-by-course="teachersByCourse"
    :teachers-loading="teachersLoading"
    @enter-course="(cn) => emit('enter-course', cn)"
    @apply-new-course="() => emit('apply-new-course')"
  />
</template>

