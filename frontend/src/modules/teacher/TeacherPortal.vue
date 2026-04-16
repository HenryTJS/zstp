<script setup>
import { computed, inject, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import colleges from '../../data/colleges.json'
import { appShellKey } from '../../appShell'
import AiAssistantWidget from '../../components/AiAssistantWidget.vue'
import TeacherPermissionRequestModal from './components/TeacherPermissionRequestModal.vue'
import TeacherEditPointModal from './components/TeacherEditPointModal.vue'
import TeacherUploadMaterialModal from './components/TeacherUploadMaterialModal.vue'
import TeacherViewMaterialsModal from './components/TeacherViewMaterialsModal.vue'
import TeacherEditProfileModal from './components/TeacherEditProfileModal.vue'
import TeacherChangePasswordModal from './components/TeacherChangePasswordModal.vue'
import TeacherMdImportInput from './components/TeacherMdImportInput.vue'
import KnowledgePointDiscussion from '../../components/KnowledgePointDiscussion.vue'
import TeacherPointTestModal from './components/TeacherPointTestModal.vue'
import TeacherProfilePage from './pages/TeacherProfilePage.vue'
import TeacherCoursesPage from './pages/TeacherCoursesPage.vue'
import TeacherCourseDetailPage from './pages/TeacherCourseDetailPage.vue'
import TeacherManagePage from './pages/TeacherManagePage.vue'
import { useTeacherPortalWorkspace } from './composables/useTeacherPortalWorkspace'
import { useTeacherProfileModule } from './composables/useTeacherProfileModule'
import { useTeacherPortalLifecycle } from './composables/useTeacherPortalLifecycle'

const props = defineProps({
  currentUser: {
    type: Object,
    required: true
  },
  activePage: {
    type: String,
    default: 'profile'
  }
})
const emit = defineEmits(['logout', 'update-user', 'login-success'])
const shell = inject(appShellKey, null)
const relayLogout = () => {
  if (typeof shell?.logout === 'function') void shell.logout()
  else emit('logout')
}
const relayUpdateUser = (patch) => {
  if (typeof shell?.updateUser === 'function') shell.updateUser(patch)
  else emit('update-user', patch)
}
const route = useRoute()
const router = useRouter()

const teacherPathSegment = computed(() => {
  const p = route.path
  if (!p.startsWith('/teacher')) return 'profile'
  let rest = p.slice('/teacher'.length)
  if (rest.startsWith('/')) rest = rest.slice(1)
  const seg = (rest.split('/')[0] || '').trim()
  return seg || 'profile'
})

const currentPage = ref(teacherPathSegment.value || props.activePage || 'profile')
watch(
  () => teacherPathSegment.value,
  (v) => {
    currentPage.value = v || props.activePage || 'profile'
  },
  { immediate: true }
)

const tp = useTeacherPortalWorkspace({ props, route, router, currentPage })
const profile = useTeacherProfileModule({ props, relayUpdateUser })

useTeacherPortalLifecycle({
  props,
  route,
  router,
  currentPage,
  loadBaseData: tp.loadBaseData,
  loadMyCourseCatalog: tp.loadMyCourseCatalog,
  refreshTeacherCoursePermissionsIfNeeded: tp.refreshTeacherCoursePermissionsIfNeeded,
  catalogCourses: tp.catalogCourses,
  selectedCourse: tp.selectedCourse,
  pointForm: tp.pointForm,
  suppressCourseWatch: tp.suppressCourseWatch,
  autoSelectCourseEnabled: tp.autoSelectCourseEnabled,
  loadCourseDetail: tp.loadCourseDetail,
  loadKnowledgePointData: tp.loadKnowledgePointData,
  loadTeacherCourseMarket: tp.loadTeacherCourseMarket,
  loadTeacherProfile: profile.loadTeacherProfile,
  applyTeacherDiscussionDeepLink: tp.applyTeacherDiscussionDeepLink,
  courseInitDone: tp.courseInitDone,
  error: tp.error
})

const {
  materials,
  points,
  isCourseRootPoint,
  selectedPointIds,
  analyticsPointName,
  pointNumberMap,
  getPointNumber,
  canPublishPointTest,
  pointTestTopicOptions,
  uploadForm,
  loading,
  message,
  error,
  uploadDialogVisible,
  uploadTargetPoint,
  viewMaterialsDialogVisible,
  viewMaterialsList,
  viewMaterialsPoint,
  pointForm,
  pointMessage,
  pointError,
  editingPoint,
  editDialogVisible,
  editPointForm,
  mdFileInputRef,
  mdImportFile,
  mdImportText,
  mdImportCourse,
  mdImportPreview,
  mdImportLoading,
  mdImportError,
  mdImportResult,
  catalogCourses,
  courseOptions,
  publishedTestCount,
  discussionVisible,
  discussionTarget,
  discussionFocusPostId,
  openDiscussionPoint,
  closeDiscussionPoint,
  pointTestVisible,
  pointTestTarget,
  openPointTest,
  closePointTest,
  applyTeacherDiscussionDeepLink,
  userInitial,
  selectedCourse,
  courseInitDone,
  suppressCourseWatch,
  autoSelectCourseEnabled,
  coursePermFetchInFlight,
  lastCoursePermFetchAt,
  refreshTeacherCoursePermissionsIfNeeded,
  loadBaseData,
  teacherHasCoursePermission,
  authorizedCourseCount,
  accessibleMaterialsCount,
  loadKnowledgePointData,
  openAnalyticsForPoint,
  loadMaterialsByKnowledgePoint,
  handleFileChange,
  openUploadModal,
  openViewMaterials,
  submitMaterial,
  switchCourse,
  openEditPoint,
  openAddPoint,
  downloadKnowledgePointMdTemplate,
  onMdFileChange,
  submitMdImport,
  handleUpdatePoint,
  handleCreatePoint,
  handleDeletePoint,
  handleDeleteSelectedPoints,
  handleDeleteMaterial,
  teacherCoursesSearch,
  teacherCoursesPage,
  allMarketCourses,
  marketCoursesLoading,
  marketCoursesError,
  myCourseCatalog,
  teachersByCourse,
  teachersByCourseLoading,
  courseDetail,
  courseDetailLoading,
  courseDetailError,
  courseMetaSaving,
  courseMetaForm,
  teacherCoursePermissionRequests,
  teacherPermissionRequestsLoading,
  teacherPermissionRequestsError,
  permissionRequestDialogVisible,
  permissionRequestMode,
  permissionRequestCourseName,
  permissionRequestText,
  permissionRequestSubmitting,
  permissionRequestError,
  teacherEnteredCourseStorageKey,
  filteredMarketCourses,
  marketTotalPages,
  pagedMarketCourses,
  permissionRequestByCourse,
  pendingTeacherCoursePermissionRequestsList,
  loadTeacherCoursePermissionRequests,
  loadTeacherCourseMarket,
  loadTeachersForMyCourseCatalog,
  formatTeachersForCourse,
  loadMyCourseCatalog,
  loadCourseDetail,
  saveCourseMeta,
  onCourseCoverFileChange,
  openPermissionRequest,
  openNewCoursePermissionRequest,
  submitPermissionRequest,
  enterCourseFromMarket,
  openCourseDetailFromMarket,
  quitCourseFromMarket,
  courseMarketPageSize
} = tp

const {
  profileForm,
  editProfileVisible,
  editProfileForm,
  changePasswordVisible,
  profileMessage,
  selectedCollege,
  openEditProfile,
  handleSaveProfile,
  openPasswordPage
} = profile

</script>

<template>
  <TeacherViewMaterialsModal
    :visible="viewMaterialsDialogVisible"
    :point-name="viewMaterialsPoint"
    :materials="viewMaterialsList"
    @close="viewMaterialsDialogVisible = false"
    @delete-material="handleDeleteMaterial"
  />
    <section class="panel-stack teacher-theme" :class="{ 'teacher-page-flat': currentPage !== 'profile' }">
    <TeacherProfilePage
      v-if="currentPage === 'profile'"
      :current-user="currentUser"
      :user-initial="userInitial"
      :profile-form="profileForm"
      :colleges="colleges"
      :selected-college="selectedCollege"
      :authorized-course-count="authorizedCourseCount"
      :accessible-materials-count="accessibleMaterialsCount"
      :published-test-count="publishedTestCount"
      :profile-message="profileMessage"
      @edit-profile="openEditProfile"
      @change-password="openPasswordPage"
      @logout="relayLogout"
    />

    <TeacherCoursesPage
      v-else-if="currentPage === 'courses'"
          :my-course-catalog="myCourseCatalog"
          :pending-permission-requests="pendingTeacherCoursePermissionRequestsList"
          :permission-requests-error="teacherPermissionRequestsError"
          :course-init-done="courseInitDone"
          :teachers-by-course="teachersByCourse"
          :teachers-loading="teachersByCourseLoading"
          @enter-course="openCourseDetailFromMarket"
          @apply-new-course="openNewCoursePermissionRequest"
        />

    <TeacherCourseDetailPage
      v-else-if="currentPage === 'course-detail'"
      :course-detail="courseDetail"
      :course-detail-loading="courseDetailLoading"
      :course-detail-error="courseDetailError"
          :teachers-text="courseDetail?.courseName ? formatTeachersForCourse(courseDetail.courseName) : ''"
      :course-meta-saving="courseMetaSaving"
      :course-meta-form="courseMetaForm"
      @update:course-meta-form="(v) => (courseMetaForm = v)"
          @enter="() => { if (courseDetail?.courseName) void enterCourseFromMarket(courseDetail.courseName) }"
          @quit="() => { if (courseDetail?.courseName) quitCourseFromMarket(courseDetail.courseName) }"
          @apply="() => openPermissionRequest(courseDetail?.courseName)"
          @save-meta="saveCourseMeta"
          @upload-cover="onCourseCoverFileChange"
        />

    <TeacherManagePage
      v-else-if="currentPage === 'manage'"
            :current-user="currentUser"
            :selected-course="selectedCourse"
            :can-configure-course="Boolean(selectedCourse && teacherHasCoursePermission(selectedCourse))"
            :points="points"
      :materials-count="materials.length"
          :selected-point-ids="selectedPointIds"
          :point-message="pointMessage"
          :point-error="pointError"
          :md-import-loading="mdImportLoading"
          :md-import-error="mdImportError"
          :md-import-result="mdImportResult"
          :get-point-number="getPointNumber"
      :download-knowledge-point-md-template="downloadKnowledgePointMdTemplate"
      :open-md-import="openMdImport"
      :open-add-point="openAddPoint"
      :handle-delete-selected-points="handleDeleteSelectedPoints"
      :open-edit-point="openEditPoint"
      :open-upload-modal="openUploadModal"
      :open-view-materials="openViewMaterials"
      :open-discussion-point="openDiscussionPoint"
      :open-point-test="openPointTest"
          :can-publish-point-test="canPublishPointTest"
      :open-analytics-for-point="openAnalyticsForPoint"
      :analytics-point-name="analyticsPointName"
      @update:selected-point-ids="selectedPointIds = $event"
      @update:analytics-point-name="(v) => (analyticsPointName = v)"
    />

    </section>
    <TeacherPermissionRequestModal
      :visible="permissionRequestDialogVisible"
      :mode="permissionRequestMode"
      :course-name="permissionRequestCourseName"
      :request-text="permissionRequestText"
      :submitting="permissionRequestSubmitting"
      :error="permissionRequestError"
      @close="permissionRequestDialogVisible = false"
      @update:course-name="(v) => (permissionRequestCourseName = v)"
      @update:request-text="(v) => (permissionRequestText = v)"
      @submit="submitPermissionRequest"
    />

    <TeacherEditPointModal
      :visible="editDialogVisible"
      :editing-point="editingPoint"
      :edit-point-form="editPointForm"
      :points="points"
      :get-point-number="getPointNumber"
      :is-course-root-point="isCourseRootPoint"
      @close="editDialogVisible = false"
      @save="editingPoint ? handleUpdatePoint() : handleCreatePoint()"
    />

    <TeacherUploadMaterialModal
      :visible="uploadDialogVisible"
      :upload-form="uploadForm"
      :upload-target-point="uploadTargetPoint"
      :loading="loading"
      :message="message"
      :error="error"
      @close="uploadDialogVisible = false"
      @file-change="handleFileChange"
      @submit="submitMaterial"
    />

    <TeacherMdImportInput ref="mdFileInputRef" @change="onMdFileChange" />

    <TeacherEditProfileModal
      :visible="editProfileVisible"
      :edit-profile-form="editProfileForm"
      :colleges="colleges"
      @close="editProfileVisible = false"
      @save="handleSaveProfile"
    />

    <TeacherChangePasswordModal
      :visible="changePasswordVisible"
      :current-user="currentUser"
      @close="changePasswordVisible = false"
    />

    <TeacherPointTestModal
      :visible="pointTestVisible && !!pointTestTarget"
      :course-name="pointTestTarget?.courseName"
      :point-name="pointTestTarget?.pointName"
      :is-course-root-anchor="!!pointTestTarget?.courseRoot"
      :topic-point-options="pointTestTopicOptions"
      :teacher-id="currentUser?.id"
      :teacher-username="currentUser?.username"
      @close="closePointTest"
    />

    <div
      v-if="discussionVisible && discussionTarget"
      class="modal-mask"
      @click.self="closeDiscussionPoint"
    >
      <div class="modal-wrapper" style="max-width: 760px; width: 94vw">
        <div class="modal-container" style="max-height: 88vh; overflow: auto">
          <button class="modal-close" type="button" aria-label="关闭" @click="closeDiscussionPoint">×</button>
          <h3 class="portal-section-title portal-section-title--sky">知识点交流区 — {{ discussionTarget.pointName }}</h3>
          <KnowledgePointDiscussion
            :course-name="discussionTarget.courseName"
            :point-name="discussionTarget.pointName"
            :current-user-id="currentUser?.id"
            :user-role="currentUser?.role"
            :focus-post-id="discussionFocusPostId"
            :disabled="false"
            :hide-title="true"
            :embedded="true"
          />
        </div>
      </div>
    </div>

    <AiAssistantWidget role="teacher" :current-user="currentUser" />
</template>

<style>
@import '@/styles/teacher/teacher-portal.css';

.teacher-page-flat > .result-card {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 !important;
}
</style>
