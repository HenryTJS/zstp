import fs from 'node:fs'
import path from 'node:path'

const dir = path.resolve('E:/zstp/frontend/src/modules/teacher/composables')
const gen = path.join(dir, '_gen')

const knowledgeBody = fs.readFileSync(path.join(gen, 'knowledge-body.txt'), 'utf8')
const courseBody = fs.readFileSync(path.join(gen, 'course-body.txt'), 'utf8')

const knowledgeFile = `import { computed, ref, watch } from 'vue'
import {
  listKnowledgePoints,
  listMaterials,
  saveKnowledgePoint,
  uploadMaterial,
  fetchMaterialsByKnowledgePoint,
  listTeacherCoursePermissions,
  countPublishedTestsByTeacherCourses
} from '../../../api/client'
import { deleteKnowledgePoint, updateKnowledgePoint, deleteMaterial } from '../../../api/point-material-ops'

export function useTeacherKnowledgeModule({ props, route, router, currentPage, myCourseCatalogRef }) {
  const myCourseCatalog = myCourseCatalogRef
${knowledgeBody}

  return {
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
    handleDeleteMaterial
  }
}
`

const courseBodyFixed = courseBody.replace(
  'const myCourseCatalog = ref([])',
  ''
)

const courseFile = `import { computed, ref, watch } from 'vue'
import {
  listCoursesByMajor,
  listCourseCatalog,
  listTeachersForCourses,
  getCourseDetail,
  updateCourseMeta,
  uploadCourseCover,
  listTeacherCoursePermissionRequests,
  createTeacherCoursePermissionRequest
} from '../../../api/client'

export function useTeacherCourseMarketModule({
  props,
  route,
  router,
  currentPage,
  myCourseCatalogRef,
  refreshTeacherCoursePermissionsIfNeeded,
  teacherHasCoursePermission,
  selectedCourseRef,
  pointFormRef,
  autoSelectCourseEnabledRef
}) {
  const myCourseCatalog = myCourseCatalogRef
  const selectedCourse = selectedCourseRef
  const pointForm = pointFormRef
  const autoSelectCourseEnabled = autoSelectCourseEnabledRef
${courseBodyFixed}

  return {
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
  }
}
`

fs.writeFileSync(path.join(dir, 'useTeacherKnowledgeModule.js'), knowledgeFile)
fs.writeFileSync(path.join(dir, 'useTeacherCourseMarketModule.js'), courseFile)
console.log('built useTeacherKnowledgeModule.js and useTeacherCourseMarketModule.js')
