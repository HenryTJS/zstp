import { computed, nextTick, ref } from 'vue'
import {
  fetchResourceProgress,
  getCourseDetail,
  listCourseCatalog,
  listTeachersForCourses,
  saveStudentState
} from '../../../../api/client'

export function useStudentCourseModule({
  currentUserRef,
  selectedMajorRef,
  joinedCoursesRef,
  selectedCourseRef,
  learningContextCourseRef,
  previewUnjoinedCourseRef,
  currentPageRef,
  router,
  persistStudentState,
  loadGraph,
  learningRecordsRef,
  wrongBookRef,
  wrongBookModalItemRef,
  profileMessageRef,
  clearExerciseUiAfterQuittingCurrentCourse
}) {
  const myCourseCatalog = ref([])
  const courseDetail = ref(null)
  const courseDetailLoading = ref(false)
  const courseDetailError = ref('')

  const courseProgressByCourse = ref({})
  const marketCourseProgressLoading = ref(false)
  let marketCourseProgressReqId = 0

  const teachersByCourse = ref({})
  const teachersByCourseLoading = ref(false)

  const marketCourseNamesForProgress = computed(() => {
    const joined = new Set((joinedCoursesRef.value || []).map((x) => String(x || '').trim()).filter(Boolean))
    const all = Array.isArray(myCourseCatalog.value) ? myCourseCatalog.value : []
    return all.filter((c) => joined.has(String(c?.courseName || '').trim())).map((c) => String(c.courseName || '').trim())
  })

  const loadMarketCourseProgress = async () => {
    const uid = currentUserRef.value?.id
    const names = marketCourseNamesForProgress.value
    if (!uid || !names.length) {
      courseProgressByCourse.value = {}
      marketCourseProgressLoading.value = false
      return
    }
    const reqId = ++marketCourseProgressReqId
    marketCourseProgressLoading.value = true
    try {
      const entries = await Promise.all(
        names.map(async (cn) => {
          try {
            const { data } = await fetchResourceProgress(uid, cn)
            const d = data && typeof data === 'object' ? data : null
            return [
              cn,
              d
                ? {
                    percent: Number(d.percent ?? 0),
                    completed: Number(d.completed ?? 0),
                    total: Number(d.total ?? 0)
                  }
                : { percent: 0, completed: 0, total: 0 }
            ]
          } catch {
            return [cn, null]
          }
        })
      )
      if (reqId !== marketCourseProgressReqId) return
      courseProgressByCourse.value = Object.fromEntries(entries)
    } finally {
      if (reqId === marketCourseProgressReqId) {
        marketCourseProgressLoading.value = false
      }
    }
  }

  const courseNamesForTeacherLookup = (availableCourses) => {
    const s = new Set()
    for (const c of availableCourses || []) {
      const t = String(c || '').trim()
      if (t) s.add(t)
    }
    for (const c of joinedCoursesRef.value || []) {
      const t = String(c || '').trim()
      if (t) s.add(t)
    }
    for (const it of myCourseCatalog.value || []) {
      const t = String(it?.courseName || '').trim()
      if (t) s.add(t)
    }
    return [...s]
  }

  const loadTeachersForMarketCourses = async (availableCourses) => {
    const names = courseNamesForTeacherLookup(availableCourses)
    if (!names.length) {
      teachersByCourse.value = {}
      return
    }
    teachersByCourseLoading.value = true
    try {
      const { data } = await listTeachersForCourses(names)
      teachersByCourse.value = data && typeof data === 'object' ? data : {}
    } catch {
      teachersByCourse.value = {}
    } finally {
      teachersByCourseLoading.value = false
    }
  }

  const refreshCoursePageData = async (availableCourses, options = {}) => {
    const { refreshTeachers = true, refreshProgress = true } = options
    if (refreshTeachers) {
      await loadTeachersForMarketCourses(availableCourses)
    }
    if (refreshProgress) {
      await loadMarketCourseProgress()
    }
  }

  const formatTeachersForCourse = (courseName) => {
    const cn = String(courseName || '').trim()
    if (!cn) return ''
    const list = teachersByCourse.value[cn]
    if (!Array.isArray(list) || !list.length) return '暂无授课教师信息'
    return list.map((t) => t?.username || '').filter(Boolean).join('、') || '暂无授课教师信息'
  }

  const loadMyCourseCatalog = async () => {
    if (!currentUserRef.value?.id) return
    try {
      const { data } = await listCourseCatalog(currentUserRef.value.id)
      myCourseCatalog.value = Array.isArray(data?.items) ? data.items : []
    } catch {
      myCourseCatalog.value = []
    }
  }

  const loadCourseDetail = async (courseName) => {
    const cn = String(courseName || '').trim()
    if (!cn) {
      courseDetail.value = null
      return
    }
    courseDetailLoading.value = true
    courseDetailError.value = ''
    try {
      const { data } = await getCourseDetail(cn, currentUserRef.value?.id)
      courseDetail.value = data || null
      try {
        const { data: tea } = await listTeachersForCourses([cn])
        if (tea && typeof tea === 'object') {
          teachersByCourse.value = { ...teachersByCourse.value, ...tea }
        }
      } catch {
        /* ignore */
      }
    } catch (e) {
      courseDetailError.value = e?.response?.data?.message || '加载课程详情失败'
      courseDetail.value = null
    } finally {
      courseDetailLoading.value = false
    }
  }

  const clearCourseDetail = () => {
    courseDetail.value = null
    courseDetailError.value = ''
    courseDetailLoading.value = false
  }

  const syncCourseDetailFromQuery = async (rawCourse) => {
    const c = Array.isArray(rawCourse) ? rawCourse[0] : rawCourse
    const cn = String(c || '').trim()
    if (!cn) {
      clearCourseDetail()
      return false
    }
    await loadCourseDetail(cn)
    return true
  }

  const openCourseDetailFromMyCourses = async (courseName) => {
    const cn = String(courseName || '').trim()
    if (!cn) return
    currentPageRef.value = 'course-detail'
    await router.push({ path: '/student/course-detail', query: { course: cn } })
    await loadCourseDetail(cn)
  }

  const joinCourse = async (courseName) => {
    const course = String(courseName || '').trim()
    if (!course || joinedCoursesRef.value.includes(course)) return
    if (!confirm(`确定要加入课程「${course}」吗？加入后即可学习该课程内容。`)) return
    joinedCoursesRef.value.push(course)
    selectedCourseRef.value = course
    await persistStudentState(false)
    await loadMyCourseCatalog()
  }

  const enterCourseFromMarket = async (courseName) => {
    const course = String(courseName || '').trim()
    if (!course) return
    if (!joinedCoursesRef.value.includes(course)) {
      courseDetailError.value = '请先加入该课程，或刷新页面后重试。'
      return
    }
    courseDetailError.value = ''
    previewUnjoinedCourseRef.value = ''
    learningContextCourseRef.value = course
    selectedCourseRef.value = course
    currentPageRef.value = 'graph'
    await router.push({ path: '/student/graph', query: { course } })
    await persistStudentState(false)
    await nextTick()
    await loadGraph()
  }

  const viewCourseWithoutJoin = async (courseName) => {
    const course = String(courseName || '').trim()
    if (!course || joinedCoursesRef.value.includes(course)) return
    learningContextCourseRef.value = ''
    previewUnjoinedCourseRef.value = course
    currentPageRef.value = 'graph'
    await router.push({ path: '/student/graph', query: { course } })
    await nextTick()
    await loadGraph()
  }

  const quitCourse = async (courseName) => {
    const course = String(courseName || '').trim()
    if (!course || !joinedCoursesRef.value.includes(course)) return
    if (
      !confirm(
        `确定要退出课程「${course}」吗？\n\n退出后将清除该课程下的学习记录、错题收藏等所有本地进度（与服务器同步后也会删除），此操作不可恢复。`
      )
    ) {
      return
    }

    const wasCurrent = selectedCourseRef.value === course
    if (learningContextCourseRef.value === course) {
      learningContextCourseRef.value = ''
      clearExerciseUiAfterQuittingCurrentCourse?.()
    }
    learningRecordsRef.value = (learningRecordsRef.value || []).filter((item) => item.course !== course)
    wrongBookRef.value = (wrongBookRef.value || []).filter((item) => item.course !== course)
    if (wrongBookModalItemRef.value?.course === course) {
      wrongBookModalItemRef.value = null
    }

    joinedCoursesRef.value = joinedCoursesRef.value.filter((c) => c !== course)

    const nextProgress = { ...courseProgressByCourse.value }
    delete nextProgress[course]
    courseProgressByCourse.value = nextProgress

    if (wasCurrent) {
      selectedCourseRef.value = joinedCoursesRef.value[0] || ''
      clearExerciseUiAfterQuittingCurrentCourse?.()
    }

    try {
      await saveStudentState({
        userId: currentUserRef.value?.id,
        major: selectedMajorRef.value || null,
        courseName: learningContextCourseRef.value || selectedCourseRef.value,
        learningRecords: learningRecordsRef.value,
        wrongBook: wrongBookRef.value,
        joinedCourses: joinedCoursesRef.value
      })
    } catch {
      profileMessageRef.value = '课程已退出，但同步服务器失败，请稍后刷新或重试。'
    }
  }

  return {
    myCourseCatalog,
    courseDetail,
    courseDetailLoading,
    courseDetailError,
    courseProgressByCourse,
    marketCourseProgressLoading,
    marketCourseNamesForProgress,
    teachersByCourse,
    teachersByCourseLoading,
    loadMarketCourseProgress,
    loadTeachersForMarketCourses,
    refreshCoursePageData,
    formatTeachersForCourse,
    loadMyCourseCatalog,
    loadCourseDetail,
    clearCourseDetail,
    syncCourseDetailFromQuery,
    openCourseDetailFromMyCourses,
    joinCourse,
    enterCourseFromMarket,
    viewCourseWithoutJoin,
    quitCourse
  }
}

