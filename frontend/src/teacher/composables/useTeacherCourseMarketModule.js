import { computed, ref, watch } from 'vue'
import {
  listCoursesByMajor,
  listCourseCatalog,
  listTeachersForCourses,
  getCourseDetail,
  updateCourseMeta,
  uploadCourseCover,
  listTeacherCoursePermissionRequests,
  createTeacherCoursePermissionRequest
} from '../../api/client'

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
// =========================
// 教师端「课程广场」
// =========================
const teacherCoursesSearch = ref('')
const courseMarketPageSize = 8
const teacherCoursesPage = ref(1)

const allMarketCourses = ref([])
const marketCoursesLoading = ref(false)
const marketCoursesError = ref('')

/** 课程名 -> 拥有授课权限的教师列表（用于广场卡片与课程介绍页） */
const teachersByCourse = ref({})
const teachersByCourseLoading = ref(false)
const courseDetail = ref(null)
const courseDetailLoading = ref(false)
const courseDetailError = ref('')
const courseMetaSaving = ref(false)
const courseMetaForm = ref({ coverUrl: '', summary: '', syllabus: '' })

// 后端存储的申请记录（教师端仅能查看自己的）
const teacherCoursePermissionRequests = ref([])
const teacherPermissionRequestsLoading = ref(false)
const teacherPermissionRequestsError = ref('')

const permissionRequestDialogVisible = ref(false)
/** join：广场已有课程；create：新课程 */
const permissionRequestMode = ref('join')
const permissionRequestCourseName = ref('')
const permissionRequestText = ref('')
const permissionRequestSubmitting = ref(false)
const permissionRequestError = ref('')

const teacherEnteredCourseStorageKey = `teacher-entered-course:${props.currentUser?.id}`

const filteredMarketCourses = computed(() => {
  const kw = (teacherCoursesSearch.value || '').trim().toLowerCase()
  if (!kw) return allMarketCourses.value
  return allMarketCourses.value.filter((c) => String(c || '').toLowerCase().includes(kw))
})

const marketTotalPages = computed(() => Math.max(1, Math.ceil(filteredMarketCourses.value.length / courseMarketPageSize)))
const pagedMarketCourses = computed(() => {
  const page = Math.min(Math.max(1, teacherCoursesPage.value), marketTotalPages.value)
  const start = (page - 1) * courseMarketPageSize
  return filteredMarketCourses.value.slice(start, start + courseMarketPageSize)
})

watch(teacherCoursesSearch, () => {
  teacherCoursesPage.value = 1
})

const permissionRequestByCourse = computed(() => {
  const map = {}
  const list = Array.isArray(teacherCoursePermissionRequests.value) ? teacherCoursePermissionRequests.value : []
  for (const r of list) {
    const cn = r?.courseName
    if (!cn || map[cn]) continue
    map[cn] = r
  }
  return map
})

const pendingTeacherCoursePermissionRequestsList = computed(() => {
  const list = Array.isArray(teacherCoursePermissionRequests.value) ? teacherCoursePermissionRequests.value : []
  return list.filter((r) => r && r.status === 'PENDING')
})

const loadTeacherCoursePermissionRequests = async () => {
  teacherPermissionRequestsLoading.value = true
  teacherPermissionRequestsError.value = ''
  try {
    const res = await listTeacherCoursePermissionRequests({ teacherId: props.currentUser.id })
    const payload = res && res.data ? res.data : res
    teacherCoursePermissionRequests.value = Array.isArray(payload) ? payload : []
  } catch (e) {
    teacherPermissionRequestsError.value = e?.response?.data?.message || e?.message || '加载申请记录失败。'
    teacherCoursePermissionRequests.value = []
  } finally {
    teacherPermissionRequestsLoading.value = false
  }
}

const loadTeacherCourseMarket = async () => {
  marketCoursesLoading.value = true
  marketCoursesError.value = ''
  try {
    const res = await listCoursesByMajor()
    const payload = res && res.data ? res.data : res
    allMarketCourses.value = Array.isArray(payload) ? payload : []

    // 权限/申请记录需要实时一些（管理员可能刚审批完）
    await refreshTeacherCoursePermissionsIfNeeded(true)
    await loadTeacherCoursePermissionRequests()
  } catch (e) {
    marketCoursesError.value = e?.response?.data?.message || e?.message || '加载课程广场失败。'
    allMarketCourses.value = []
  } finally {
    marketCoursesLoading.value = false
  }
}

const loadTeachersForMyCourseCatalog = async () => {
  const names = (myCourseCatalog.value || []).map((x) => String(x?.courseName || '').trim()).filter(Boolean)
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

const formatTeachersForCourse = (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return ''
  const list = teachersByCourse.value[cn]
  if (!Array.isArray(list) || !list.length) return '暂无授课教师信息'
  return list.map((t) => t?.username || '').filter(Boolean).join('、') || '暂无授课教师信息'
}

const loadMyCourseCatalog = async () => {
  if (!props.currentUser?.id) return
  try {
    const { data } = await listCourseCatalog(props.currentUser.id)
    const items = Array.isArray(data?.items) ? data.items : []
    myCourseCatalog.value = items.filter((x) => x?.hasAccess)
  } catch {
    myCourseCatalog.value = []
  }
  await loadTeachersForMyCourseCatalog()
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
    const { data } = await getCourseDetail(cn, props.currentUser?.id)
    courseDetail.value = data || null
    courseMetaForm.value = {
      coverUrl: data?.coverUrl || '',
      summary: data?.summary || '',
      syllabus: data?.syllabus || ''
    }
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
    courseMetaForm.value = { coverUrl: '', summary: '', syllabus: '' }
  } finally {
    courseDetailLoading.value = false
  }
}

const saveCourseMeta = async () => {
  if (!courseDetail.value?.courseName) return
  courseMetaSaving.value = true
  courseDetailError.value = ''
  let ok = false
  try {
    const { data } = await updateCourseMeta({
      userId: props.currentUser.id,
      courseName: courseDetail.value.courseName,
      coverUrl: courseMetaForm.value.coverUrl,
      summary: courseMetaForm.value.summary,
      syllabus: courseMetaForm.value.syllabus
    })
    // 合并而非整体替换：旧版后端 PUT /courses/meta 曾不返回 hasAccess/canEditMeta，会导致误判为「申请权限」
    courseDetail.value = { ...(courseDetail.value || {}), ...(data || {}) }
    await loadMyCourseCatalog()
    ok = true
  } catch (e) {
    courseDetailError.value = e?.response?.data?.message || '保存失败'
  } finally {
    courseMetaSaving.value = false
  }
  return ok
}

const onCourseCoverFileChange = async (e) => {
  const file = e?.target?.files?.[0]
  if (!file || !courseDetail.value?.courseName) return
  const fileName = String(file?.name || '').toLowerCase()
  const mime = String(file?.type || '').toLowerCase()
  const isJpgOrPng =
    mime === 'image/jpeg' ||
    mime === 'image/png' ||
    fileName.endsWith('.jpg') ||
    fileName.endsWith('.jpeg') ||
    fileName.endsWith('.png')
  if (!isJpgOrPng) {
    courseDetailError.value = '封面仅支持 JPG/PNG。手机请关闭“高效格式(HEIC)”后重试。'
    if (e?.target) e.target.value = ''
    return
  }
  courseMetaSaving.value = true
  courseDetailError.value = ''
  try {
    const fd = new FormData()
    fd.append('userId', String(props.currentUser.id))
    fd.append('courseName', String(courseDetail.value.courseName))
    fd.append('file', file)
    const { data } = await uploadCourseCover(fd)
    const url = String(data?.coverUrl || '').trim()
    if (url) {
      courseMetaForm.value = { ...courseMetaForm.value, coverUrl: url }
      const saved = await saveCourseMeta()
      if (!saved) return
      await loadCourseDetail(courseDetail.value.courseName)
    } else {
      courseDetailError.value = '封面上传成功但未返回可用地址，请稍后重试。'
    }
  } catch (e2) {
    courseDetailError.value = e2?.response?.data?.message || '封面上传失败'
  } finally {
    courseMetaSaving.value = false
    if (e?.target) e.target.value = ''
  }
}

watch(
  currentPage,
  (v) => {
    if (v === 'courses') {
      void loadTeacherCourseMarket()
      void loadMyCourseCatalog()
    }
    if (v === 'course-detail') {
      const c = Array.isArray(route.query.course) ? route.query.course[0] : route.query.course
      const cn = String(c || '').trim()
      if (cn) void loadCourseDetail(cn)
    }
  },
  { immediate: false }
)

const openPermissionRequest = (courseName) => {
  permissionRequestMode.value = 'join'
  permissionRequestCourseName.value = String(courseName || '').trim()
  permissionRequestText.value = ''
  permissionRequestError.value = ''
  permissionRequestDialogVisible.value = true
}

const openNewCoursePermissionRequest = () => {
  permissionRequestMode.value = 'create'
  permissionRequestCourseName.value = ''
  permissionRequestText.value = ''
  permissionRequestError.value = ''
  permissionRequestDialogVisible.value = true
}

const submitPermissionRequest = async () => {
  permissionRequestError.value = ''
  if (permissionRequestMode.value === 'create' && !String(permissionRequestCourseName.value || '').trim()) {
    permissionRequestError.value = '请填写新课程名称。'
    return
  }
  if (permissionRequestMode.value === 'join' && !permissionRequestCourseName.value) {
    permissionRequestError.value = '课程名为空。'
    return
  }
  if (!permissionRequestText.value || !permissionRequestText.value.trim()) {
    permissionRequestError.value = '请填写获得权限的申请书内容。'
    return
  }

  permissionRequestSubmitting.value = true
  try {
    await createTeacherCoursePermissionRequest({
      teacherId: props.currentUser.id,
      courseName: permissionRequestCourseName.value.trim(),
      requestText: permissionRequestText.value.trim(),
      requestKind: permissionRequestMode.value === 'create' ? 'CREATE_NEW' : 'JOIN_EXISTING'
    })
    permissionRequestDialogVisible.value = false
    await loadTeacherCoursePermissionRequests()
    await refreshTeacherCoursePermissionsIfNeeded(true)
    await loadMyCourseCatalog()
  } catch (e) {
    permissionRequestError.value = e?.response?.data?.message || e?.message || '提交申请失败。'
  } finally {
    permissionRequestSubmitting.value = false
  }
}

const enterCourseFromMarket = async (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return
  courseDetailError.value = ''
  if (!teacherHasCoursePermission(cn)) {
      await refreshTeacherCoursePermissionsIfNeeded(true)
      await loadMyCourseCatalog()
      if (!teacherHasCoursePermission(cn)) {
        courseDetailError.value = '当前账号暂无该课程授课权限，请稍后再试或联系管理员。'
        return
      }
    }
  if (selectedCourse.value === cn) {
    // 已进入该课程：允许“再次点击”但不重复切换与刷新
    await router.push({ path: '/teacher/manage', query: { course: cn } })
    return
  }

  autoSelectCourseEnabled.value = false
  selectedCourse.value = cn
  pointForm.value.courseName = cn

  try {
    localStorage.setItem(teacherEnteredCourseStorageKey, cn)
  } catch (e) {}

  // 携带显式 course 参数，并清空可能残留的讨论深链参数（dc/dp/dpost）
  // 避免知识点页被 deepLink 覆盖课程（造成“点A跳B”的偶发情况）
  await router.push({ path: '/teacher/manage', query: { course: cn } })
}

const openCourseDetailFromMarket = async (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return
  await router.push({ path: '/teacher/course-detail', query: { course: cn } })
}

const quitCourseFromMarket = (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return
  if (selectedCourse.value !== cn) return

  const ok = confirm(`确定要退出课程「${cn}」吗？退出后将停止对该课程的资料管理。`)
  if (!ok) return

  autoSelectCourseEnabled.value = false
  selectedCourse.value = ''
  pointForm.value.courseName = ''

  try {
    localStorage.setItem(teacherEnteredCourseStorageKey, '')
  } catch (e) {}

  // 清空可能残留的讨论深链参数（dc/dp/dpost）
  void loadMyCourseCatalog()
  router.push({ path: '/teacher/courses', query: {} })
}

watch(
  () => route.query.course,
  (c) => {
    if (currentPage.value !== 'course-detail') return
    const cn = String(Array.isArray(c) ? c[0] : c || '').trim()
    if (cn) void loadCourseDetail(cn)
    else {
      courseDetail.value = null
      courseDetailError.value = ''
    }
  }
)

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
