<script setup>
import TeacherKnowledge from '../components/TeacherKnowledge.vue'
import TeacherCourseDashboard from '../components/TeacherCourseDashboard.vue'
import TeacherCourseProgress from '../components/TeacherCourseProgress.vue'
import TeacherStudentAnalytics from '../components/TeacherStudentAnalytics.vue'

const props = defineProps({
  currentUser: { type: Object, required: true },
  selectedCourse: { type: String, default: '' },
  points: { type: Array, required: true },
  materialsCount: { type: Number, default: 0 },

  selectedPointIds: { type: Array, required: true },
  pointMessage: { type: String, default: '' },
  pointError: { type: String, default: '' },
  mdImportLoading: { type: Boolean, default: false },
  mdImportError: { type: String, default: '' },
  mdImportResult: { type: String, default: '' },
  getPointNumber: { type: Function, required: true },

  downloadKnowledgePointMdTemplate: { type: Function, required: true },
  openMdImport: { type: Function, required: true },
  openAddPoint: { type: Function, required: true },
  handleDeleteSelectedPoints: { type: Function, required: true },
  openEditPoint: { type: Function, required: true },
  openUploadModal: { type: Function, required: true },
  openViewMaterials: { type: Function, required: true },
  openDiscussionPoint: { type: Function, required: true },
  openPointTest: { type: Function, required: true },
  canPublishPointTest: { type: Function, required: true },
  openAnalyticsForPoint: { type: Function, required: true },

  analyticsPointName: { type: String, default: '' }
})

const emit = defineEmits(['update:selected-point-ids', 'update:analytics-point-name'])
</script>

<template>
  <div id="teacher-course-dashboard-panel">
    <TeacherCourseDashboard
      :current-user="currentUser"
      :selected-course="selectedCourse"
      :points="points"
      :material-count="materialsCount"
    />
  </div>

  <TeacherKnowledge
    class="ui-mt-12"
    :points="points"
    :selected-point-ids="selectedPointIds"
    :point-message="pointMessage"
    :point-error="pointError"
    :md-import-loading="mdImportLoading"
    :md-import-error="mdImportError"
    :md-import-result="mdImportResult"
    :get-point-number="getPointNumber"
    @update:selected-point-ids="(v) => emit('update:selected-point-ids', v)"
    :on-download-template="downloadKnowledgePointMdTemplate"
    :on-open-md-import="openMdImport"
    :on-open-add-point="openAddPoint"
    :on-delete-selected-points="handleDeleteSelectedPoints"
    :on-open-edit-point="openEditPoint"
    :on-open-upload-modal="openUploadModal"
    :on-open-view-materials="openViewMaterials"
    :on-open-discussion="openDiscussionPoint"
    :on-open-point-test="openPointTest"
    :can-publish-point-test="canPublishPointTest"
    :on-open-analytics="openAnalyticsForPoint"
  />

  <div id="teacher-course-progress-panel" class="ui-mt-12">
    <TeacherCourseProgress :current-user="currentUser" :selected-course="selectedCourse" />
  </div>

  <div id="teacher-student-analytics-panel" class="ui-mt-12">
    <TeacherStudentAnalytics
      :current-user="currentUser"
      :selected-course="selectedCourse"
      :points="points"
      :point-name="analyticsPointName"
      :get-point-number="getPointNumber"
      @update:point-name="(v) => emit('update:analytics-point-name', v)"
    />
  </div>
</template>

