import { nextTick, onMounted, watch } from 'vue'

/**
 * 学生门户：路由深链、切页副作用、课程广场数据刷新、首屏 bootstrap 的集中编排。
 */
export function useStudentPortalOrchestration({
  route,
  router,
  stateHydrated,
  currentPage,
  effectivePage,
  learningContextCourse,
  kpCascade1,
  kpCascade2,
  kpCascade3,
  loadExamPoints,
  schedulePersistStudentState,
  loadGraph,
  graphData,
  selectedKnowledgePoint,
  previewUnjoinedCourse,
  canShowGraphPage,
  loadResourcesByKnowledgePoint,
  loadLearningSuggestionsFor,
  loadMajorRelevanceFor,
  closeWrongBookModal,
  loadSavedExams,
  joinedCourses,
  availableCourses,
  myCourseCatalog,
  pendingGraphPointLabel,
  discussionFocusPostId,
  selectedCourse,
  refreshCoursePageData,
  marketCourseNamesForProgress,
  getCurrentUserId,
  selectedMajor1,
  selectedMajor2,
  selectedMajor3,
  syncCourseDetailFromQuery,
  clearCourseDetail,
  canStudyCurrentCourse,
  loadStudentState,
  loadMyCourseCatalog
}) {
  const applyDiscussionDeepLinkFromRoute = async () => {
    const rawDc = route.query.dc
    const rawDp = route.query.dp
    if (!rawDc || !rawDp) return
    const dc = Array.isArray(rawDc) ? rawDc[0] : rawDc
    const dp = Array.isArray(rawDp) ? rawDp[0] : rawDp
    const courseName = String(dc || '').trim()
    const pointName = String(dp || '').trim()
    const rawPost = route.query.dpost
    const dpostRaw = Array.isArray(rawPost) ? rawPost[0] : rawPost
    const postNum = dpostRaw != null && dpostRaw !== '' ? Number(dpostRaw) : NaN

    const nextQ = { ...route.query }
    delete nextQ.dc
    delete nextQ.dp
    delete nextQ.dpost

    if (!joinedCourses.value.includes(courseName)) {
      discussionFocusPostId.value = null
      pendingGraphPointLabel.value = ''
      try {
        await router.replace({ path: route.path, query: nextQ })
      } catch {
        /* ignore */
      }
      return
    }

    discussionFocusPostId.value = Number.isFinite(postNum) ? postNum : null
    pendingGraphPointLabel.value = pointName
    previewUnjoinedCourse.value = ''
    learningContextCourse.value = courseName
    selectedCourse.value = courseName
    currentPage.value = 'graph'
    try {
      await router.replace({ path: route.path, query: nextQ })
    } catch {
      /* ignore */
    }
    await nextTick()
    await loadGraph()
  }

  const applyCourseQueryFromRoute = async () => {
    const rawCourse = route.query.course
    if (!rawCourse) return false
    const raw = Array.isArray(rawCourse) ? rawCourse[0] : rawCourse
    const courseName = String(raw || '').trim()
    if (!courseName) return false

    if (joinedCourses.value.includes(courseName)) {
      previewUnjoinedCourse.value = ''
      learningContextCourse.value = courseName
      selectedCourse.value = courseName
      currentPage.value = 'graph'
      pendingGraphPointLabel.value = ''
      await nextTick()
      await loadGraph()
      return true
    }

    if (availableCourses.value.includes(courseName)) {
      learningContextCourse.value = ''
      previewUnjoinedCourse.value = courseName
      currentPage.value = 'graph'
      pendingGraphPointLabel.value = ''
      await nextTick()
      await loadGraph()
      return true
    }

    return false
  }

  watch(
    learningContextCourse,
    () => {
      kpCascade1.value = ''
      kpCascade2.value = ''
      kpCascade3.value = ''
      loadExamPoints()
      schedulePersistStudentState()
      if (currentPage.value === 'graph') {
        void loadGraph()
      }
    },
    { immediate: true }
  )

  watch(currentPage, (v) => {
    if (v !== 'review') closeWrongBookModal()
  })

  watch(selectedCourse, () => {
    schedulePersistStudentState()
  })

  const refreshCourseModuleForCoursesPage = async () => {
    if (currentPage.value !== 'courses') return
    await refreshCoursePageData(availableCourses.value, { refreshTeachers: true, refreshProgress: false })
  }

  watch(
    [currentPage, availableCourses, joinedCourses, myCourseCatalog],
    refreshCourseModuleForCoursesPage,
    { deep: true }
  )

  watch(
    () => [effectivePage.value, marketCourseNamesForProgress.value.slice().sort().join('|'), getCurrentUserId?.()],
    () => {
      if (effectivePage.value !== 'courses') return
      void refreshCoursePageData(availableCourses.value, { refreshTeachers: false, refreshProgress: true })
    },
    { flush: 'post' }
  )

  watch([selectedMajor1, selectedMajor2, selectedMajor3], () => {
    if (currentPage.value === 'graph' && selectedKnowledgePoint.value) {
      void loadMajorRelevanceFor(selectedKnowledgePoint.value)
    }
  })

  watch(
    () => currentPage.value,
    async (value) => {
      if (value === 'review') {
        await loadSavedExams()
      }
      if (value === 'graph') {
        if (!canShowGraphPage.value) return
        if (!graphData.value.nodes?.length) {
          await loadGraph()
        } else {
          const kp =
            selectedKnowledgePoint.value ||
            graphData.value.nodes.find((n) => n.id === 'root')?.label ||
            ''
          if (kp && !previewUnjoinedCourse.value) {
            await loadResourcesByKnowledgePoint(kp)
            void loadLearningSuggestionsFor(kp)
            void loadMajorRelevanceFor(kp)
          }
        }
      }
    }
  )

  watch(
    () => route.query.dc,
    () => {
      void applyDiscussionDeepLinkFromRoute()
    }
  )

  watch(
    () => route.query.course,
    () => {
      if (!stateHydrated.value) return
      if (effectivePage.value === 'course-detail') {
        void syncCourseDetailFromQuery(route.query.course)
        return
      }
      void applyCourseQueryFromRoute()
    }
  )

  onMounted(async () => {
    await loadStudentState()
    await loadMyCourseCatalog()
    const hadCourseQuery = Boolean(route.query.course)
    const isDetailPage = effectivePage.value === 'course-detail'
    if (isDetailPage && hadCourseQuery) {
      await syncCourseDetailFromQuery(route.query.course)
    } else if (isDetailPage && !hadCourseQuery) {
      clearCourseDetail()
    }
    const appliedCourseQuery = (!isDetailPage && hadCourseQuery) ? await applyCourseQueryFromRoute() : false
    const shouldApplyDiscussionDeepLink = !appliedCourseQuery && Boolean(route.query.dc && route.query.dp)
    if (shouldApplyDiscussionDeepLink) {
      await applyDiscussionDeepLinkFromRoute()
    } else if (canStudyCurrentCourse.value || previewUnjoinedCourse.value) {
      loadGraph()
    }
    await loadSavedExams()
  })
}
