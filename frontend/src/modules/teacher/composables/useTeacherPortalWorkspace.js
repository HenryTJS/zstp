import { createTeacherSharedRefs } from './createTeacherSharedRefs'
import { useTeacherCourseMarketModule } from './useTeacherCourseMarketModule'
import { useTeacherKnowledgeModule } from './useTeacherKnowledgeModule'

export function useTeacherPortalWorkspace({ props, route, router, currentPage }) {
  const { myCourseCatalog } = createTeacherSharedRefs()

  const knowledge = useTeacherKnowledgeModule({
    props,
    route,
    router,
    currentPage,
    myCourseCatalogRef: myCourseCatalog
  })

  const course = useTeacherCourseMarketModule({
    props,
    route,
    router,
    currentPage,
    myCourseCatalogRef: myCourseCatalog,
    refreshTeacherCoursePermissionsIfNeeded: knowledge.refreshTeacherCoursePermissionsIfNeeded,
    teacherHasCoursePermission: knowledge.teacherHasCoursePermission,
    selectedCourseRef: knowledge.selectedCourse,
    pointFormRef: knowledge.pointForm,
    autoSelectCourseEnabledRef: knowledge.autoSelectCourseEnabled
  })

  return {
    ...knowledge,
    ...course
  }
}
