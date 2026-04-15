import { onMounted, watch } from 'vue'

export function useTeacherPortalLifecycle({
  props,
  route,
  router,
  currentPage,
  loadBaseData,
  loadMyCourseCatalog,
  refreshTeacherCoursePermissionsIfNeeded,
  catalogCourses,
  selectedCourse,
  pointForm,
  suppressCourseWatch,
  autoSelectCourseEnabled,
  loadCourseDetail,
  loadKnowledgePointData,
  loadTeacherCourseMarket,
  loadTeacherProfile,
  applyTeacherDiscussionDeepLink,
  courseInitDone,
  error
}) {
  onMounted(async () => {
    try {
      const storageKey = `teacher-entered-course:${props.currentUser?.id}`
      try {
        const stored = localStorage.getItem(storageKey)
        if (stored !== null) {
          autoSelectCourseEnabled.value = false
          selectedCourse.value = stored
          pointForm.value.courseName = stored
        }
      } catch {
        /* ignore localStorage failures */
      }

      await loadBaseData()
      await loadMyCourseCatalog()
      await refreshTeacherCoursePermissionsIfNeeded(true)

      const incomingCourse = route.query.course ? String(route.query.course).trim() : ''
      const onCourseDetailPage = currentPage.value === 'course-detail'
      if (incomingCourse) {
        const opts = Array.isArray(catalogCourses.value) ? catalogCourses.value : []
        if (opts.includes(incomingCourse)) {
          autoSelectCourseEnabled.value = false
          suppressCourseWatch.value = true
          selectedCourse.value = incomingCourse
          pointForm.value.courseName = incomingCourse
          suppressCourseWatch.value = false
        }
        try {
          const nextQ = { ...route.query }
          if (!onCourseDetailPage) {
            delete nextQ.course
          }
          delete nextQ.dc
          delete nextQ.dp
          delete nextQ.dpost
          const same =
            String(route.query.course || '') === String(nextQ.course || '') &&
            String(route.query.dc || '') === String(nextQ.dc || '') &&
            String(route.query.dp || '') === String(nextQ.dp || '') &&
            String(route.query.dpost || '') === String(nextQ.dpost || '')
          if (!same) {
            await router.replace({ path: route.path, query: nextQ })
          }
        } catch (e) {
          /* ignore */
        }
      }

      if (onCourseDetailPage && incomingCourse) {
        await loadCourseDetail(incomingCourse)
      }

      await loadKnowledgePointData()
      courseInitDone.value = true
      loadTeacherProfile()
      if (currentPage.value === 'courses') {
        await loadTeacherCourseMarket()
      }
      const shouldApplyDeepLink = !incomingCourse && route.query.dc && route.query.dp
      if (shouldApplyDeepLink) {
        await applyTeacherDiscussionDeepLink()
      }
    } catch (e) {
      error.value = '加载教师端数据失败，请确认后端与数据库已启动。'
    }
  })

  watch(
    () => route.query.dc,
    async (dc) => {
      if (!courseInitDone.value || !dc) return
      await applyTeacherDiscussionDeepLink()
    }
  )
}
