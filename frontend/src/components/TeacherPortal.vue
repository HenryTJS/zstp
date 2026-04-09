<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import colleges from '../data/colleges.json'
import {
  listKnowledgePoints,
  listMaterials,
  saveKnowledgePoint,
  uploadMaterial,
  updateUser,
  fetchMaterialsByKnowledgePoint,
  listTeacherCoursePermissions,
  listCoursesByMajor,
  listCourseCatalog,
  getCourseDetail,
  updateCourseMeta,
  uploadCourseCover,
  listTeacherCoursePermissionRequests,
  createTeacherCoursePermissionRequest,
  countPublishedTestsByTeacherCourses
} from '../api/client'
import { deleteKnowledgePoint, updateKnowledgePoint, deleteMaterial } from '../api/point-material-ops'
import AiAssistantWidget from './AiAssistantWidget.vue'
import TeacherCourses from './TeacherCourses.vue'
import CourseDetailPanel from './CourseDetailPanel.vue'
import TeacherPermissionRequestModal from './TeacherPermissionRequestModal.vue'
import TeacherKnowledge from './TeacherKnowledge.vue'
import TeacherEditPointModal from './TeacherEditPointModal.vue'
import TeacherUploadMaterialModal from './TeacherUploadMaterialModal.vue'
import TeacherViewMaterialsModal from './TeacherViewMaterialsModal.vue'
import TeacherEditProfileModal from './TeacherEditProfileModal.vue'
import TeacherChangePasswordModal from './TeacherChangePasswordModal.vue'
import TeacherMdImportInput from './TeacherMdImportInput.vue'
import KnowledgePointDiscussion from './KnowledgePointDiscussion.vue'
import TeacherPointTestModal from './TeacherPointTestModal.vue'
import TeacherStudentAnalytics from './TeacherStudentAnalytics.vue'

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
const route = useRoute()
const router = useRouter()
// 当前激活子页，优先使用路由 param，再使用 props 作为回退
const currentPage = ref(route.params.page || props.activePage || 'profile')
// 同步路由 param 到本地 ref
watch(() => route.params.page, (v) => {
  currentPage.value = v || props.activePage || 'profile'
})

// 数据
const materials = ref([])
const points = ref([])

/** 与课程同名的根知识点，不可删除、不可编辑名称与父级 */
const isCourseRootPoint = (item) => Boolean(item?.courseRoot)

// =========================
// 知识点编号（章/节/知识点）
// 例如：1、1.1、1.1.1
// 编号用于“渲染展示”和“父级选择提示”，不影响后端数据结构
// =========================
const selectedPointIds = ref([])
const analyticsPointName = ref('')

/** 与列表接口一致：同级按 sortOrder 再按 id，避免仅按 id 时「第10章」等顺序与编号错位 */
const cmpNodeOrder = (a, b) => {
  const oa = Number(a?.sortOrder ?? 0)
  const ob = Number(b?.sortOrder ?? 0)
  if (oa !== ob) return oa - ob

  // 如果 sortOrder 都是 0（MD 导入未传 sortOrder 的常见情况），
  // 用 createdAt（导入/插入顺序）来稳定确定“最近父节点”的选择。
  const ta = a?.createdAt ? Date.parse(String(a.createdAt)) : NaN
  const tb = b?.createdAt ? Date.parse(String(b.createdAt)) : NaN
  const fa = Number.isFinite(ta)
  const fb = Number.isFinite(tb)
  if (fa && fb && ta !== tb) return ta - tb
  if (fa && !fb) return -1
  if (!fa && fb) return 1

  return (Number(a?.id ?? 0) - Number(b?.id ?? 0))
}

const pointNumberMap = computed(() => {
  const outById = new Map() // key: String(id)
  const list = Array.isArray(points.value) ? points.value : []
  if (!list.length) return outById

  const nodes = list.slice().sort(cmpNodeOrder)
  const childrenByParentId = new Map()
  const lastByTrimPointName = new Map() // 兼容历史数据：pointName(trim) -> 最近节点

  for (const p of nodes) {
    let parentId = p?.parentId == null ? null : Number(p.parentId)
    // 兼容旧数据：若没有 parentId，再回退到 parentPoint 名称推断
    if (parentId == null) {
      const rawParent = p.parentPoint == null || p.parentPoint === undefined ? null : String(p.parentPoint).trim()
      parentId = rawParent ? lastByTrimPointName.get(rawParent)?.id ?? null : null
    }
    if (!childrenByParentId.has(parentId)) childrenByParentId.set(parentId, [])
    childrenByParentId.get(parentId).push(p)

    const selfName = String(p.pointName || '').trim()
    if (selfName) lastByTrimPointName.set(selfName, p)
  }

  const assign = (parentId, prefixParts, guard) => {
    // guard 用于避免极端脏数据造成的循环（以节点 id 为界）
    const visiting = new Set(guard || [])
    if (prefixParts.length > 12) return
    if (parentId != null) {
      const key = String(parentId)
      if (visiting.has(key)) return
      visiting.add(key)
    }

    const children = (childrenByParentId.get(parentId) || []).slice().sort(cmpNodeOrder)
    let idx = 0
    for (const child of children) {
      if (isCourseRootPoint(child)) continue
      idx += 1
      const parts = [...prefixParts, idx]
      outById.set(String(child.id), parts.join('.'))
      assign(child.id, parts, visiting)
    }
  }

  // 顶层：parentPoint 为 null 的节点（且非课程根）作为“第 x 章”
  assign(null, [], new Set())

  // 兼容：如果存在“课程根”作为真实父级的存储形态，也从课程根继续编号
  const courseRoot = nodes.find((p) => isCourseRootPoint(p))
  if (courseRoot?.id != null) {
    assign(courseRoot.id, [], new Set())
  }

  return outById
})

const getPointNumber = (pointOrName) => {
  if (pointOrName && typeof pointOrName === 'object') {
    return pointNumberMap.value.get(String(pointOrName.id)) || ''
  }
  const name = String(pointOrName || '').trim()
  if (!name) return ''
  const exact = (Array.isArray(points.value) ? points.value : []).find((p) => String(p?.pointName || '').trim() === name)
  return exact ? pointNumberMap.value.get(String(exact.id)) || '' : ''
}

/** 任意层级知识点均可发布测试（课程根仍为「期末测试」文案） */
const canPublishPointTest = (item) =>
  Boolean(item?.courseName && String(item?.pointName || '').trim())

/** 锚点及其所有下级知识点（深度优先，编号排序），供每题选择考查点 */
const buildSubtreeTopicOptions = (anchorPointId, allPoints, getPointNumberFn) => {
  const list = Array.isArray(allPoints) ? allPoints : []
  const anchor = anchorPointId == null ? null : Number(anchorPointId)
  if (anchor == null) return []
  // 兼容旧数据：部分点只有 parentPoint 没有 parentId
  // 这里沿用编号计算的“最近同名节点”回退策略，保证下属知识点能被正确挂载到锚点下
  const nodes = list.slice().sort(cmpNodeOrder)
  const byParent = new Map()
  const lastByTrimPointName = new Map() // pointName(trim) -> 最近节点
  for (const p of nodes) {
    let par = p?.parentId == null ? null : Number(p.parentId)
    if (par == null) {
      const rawParent = p?.parentPoint == null ? null : String(p.parentPoint).trim()
      par = rawParent ? (lastByTrimPointName.get(rawParent)?.id ?? null) : null
    }
    if (!byParent.has(par)) byParent.set(par, [])
    byParent.get(par).push(p)

    const selfName = String(p?.pointName || '').trim()
    if (selfName) lastByTrimPointName.set(selfName, p)
  }
  const orderedNodes = []
  const walk = (id) => {
    const node = nodes.find((p) => Number(p.id) === Number(id))
    if (!node) return
    orderedNodes.push(node)
    const rawKids = byParent.get(Number(id)) || []
    const kids = rawKids.slice().sort((a, b) => {
      const na = String(getPointNumberFn(a) || a.pointName || '')
      const nb = String(getPointNumberFn(b) || b.pointName || '')
      return na.localeCompare(nb, undefined, { numeric: true })
    })
    for (const c of kids) walk(c.id)
  }
  walk(anchor)
  return orderedNodes.map((node) => {
    const num = getPointNumberFn(node) || ''
    return {
      value: node.pointName,
      label: num ? `${num} ${node.pointName}` : node.pointName
    }
  })
}

const pointTestTopicOptions = computed(() => {
  const t = pointTestTarget.value
  if (t?.id == null || !Array.isArray(points.value) || !points.value.length) return []
  return buildSubtreeTopicOptions(t.id, points.value, getPointNumber)
})

// 上传表单与状态
const uploadForm = ref({ title: '', description: '', course: '', chapter: '', section: '', point: '', category: 'ATTACHMENT', files: [] })
const loading = ref(false)
const message = ref('')
const error = ref('')
const uploadDialogVisible = ref(false)
const uploadTargetPoint = ref(null)
const viewMaterialsDialogVisible = ref(false)
const viewMaterialsList = ref([])
const viewMaterialsPoint = ref('')

// 知识点表单：课程由管理员分配权限后才可见，默认不预设课程
const pointForm = ref({ courseName: '' })
const pointMessage = ref('')
const pointError = ref('')

// 编辑弹窗
const editingPoint = ref(null)
const editDialogVisible = ref(false)
const editPointForm = ref({ courseName: '', pointName: '', parentId: null, parentPoint: '' })

// =========================
// MD 导入知识点（无弹窗：用系统文件选择器）
// =========================
const mdFileInputRef = ref(null)
const mdImportFile = ref(null)
const mdImportText = ref('')
const mdImportCourse = ref('')
const mdImportPreview = ref([]) // [{ courseName, pointName, parentPoint }]
const mdImportLoading = ref(false)
const mdImportError = ref('')
const mdImportResult = ref('')

const catalogCourses = ref([])

const courseOptions = computed(() => (Array.isArray(catalogCourses.value) ? catalogCourses.value : []))

const profileMessage = ref('')
const selectedCollege = ref('')
const publishedTestCount = ref(0)

// 公告已并入「通知」弹窗；教师端不再单独提供公告页。
 
// =========================
// 教师端「课程广场」
// =========================
const teacherCoursesSearch = ref('')
const courseMarketPageSize = 8
const teacherCoursesPage = ref(1)

const allMarketCourses = ref([])
const marketCoursesLoading = ref(false)
const marketCoursesError = ref('')
const myCourseCatalog = ref([])
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

const loadMyCourseCatalog = async () => {
  if (!props.currentUser?.id) return
  try {
    const { data } = await listCourseCatalog(props.currentUser.id)
    const items = Array.isArray(data?.items) ? data.items : []
    myCourseCatalog.value = items.filter((x) => x?.hasAccess)
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
    const { data } = await getCourseDetail(cn, props.currentUser?.id)
    courseDetail.value = data || null
    courseMetaForm.value = {
      coverUrl: data?.coverUrl || '',
      summary: data?.summary || '',
      syllabus: data?.syllabus || ''
    }
  } catch (e) {
    courseDetailError.value = e?.response?.data?.message || '加载课程详情失败'
    courseDetail.value = null
  } finally {
    courseDetailLoading.value = false
  }
}

const saveCourseMeta = async () => {
  if (!courseDetail.value?.courseName) return
  courseMetaSaving.value = true
  courseDetailError.value = ''
  try {
    const { data } = await updateCourseMeta({
      userId: props.currentUser.id,
      courseName: courseDetail.value.courseName,
      coverUrl: courseMetaForm.value.coverUrl,
      summary: courseMetaForm.value.summary,
      syllabus: courseMetaForm.value.syllabus
    })
    courseDetail.value = data || courseDetail.value
    await loadMyCourseCatalog()
  } catch (e) {
    courseDetailError.value = e?.response?.data?.message || '保存失败'
  } finally {
    courseMetaSaving.value = false
  }
}

const onCourseCoverFileChange = async (e) => {
  const file = e?.target?.files?.[0]
  if (!file || !courseDetail.value?.courseName) return
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
      await saveCourseMeta()
      await loadCourseDetail(courseDetail.value.courseName)
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
      void loadCourseDetail(c)
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
  } catch (e) {
    permissionRequestError.value = e?.response?.data?.message || e?.message || '提交申请失败。'
  } finally {
    permissionRequestSubmitting.value = false
  }
}

const enterCourseFromMarket = (courseName) => {
  const cn = String(courseName || '').trim()
  if (!cn) return
  if (!Array.isArray(catalogCourses.value) || !catalogCourses.value.includes(cn)) return
  if (selectedCourse.value === cn) {
    // 已进入该课程：允许“再次点击”但不重复切换与刷新
    router.push({ path: '/teacher/manage', query: { course: cn } })
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
  router.push({ path: '/teacher/manage', query: { course: cn } })
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
  router.push({ path: '/teacher/courses', query: {} })
}

// 与学生端保持一致的个人信息编辑/修改密码逻辑（简化）
const profileForm = ref({ username: props.currentUser.username || '', email: props.currentUser.email || '' })
const editProfileVisible = ref(false)
const editProfileForm = ref({ username: '', email: '', college: '' })
const changePasswordVisible = ref(false)

const discussionVisible = ref(false)
const discussionTarget = ref(null)
/** 通知深链：交流区滚动锚点 */
const discussionFocusPostId = ref(null)
const openDiscussionPoint = (item, focusPostIdOrUnset) => {
  if (!item) return
  if (arguments.length >= 2) {
    if (focusPostIdOrUnset == null || focusPostIdOrUnset === '') {
      discussionFocusPostId.value = null
    } else {
      const n = Number(focusPostIdOrUnset)
      discussionFocusPostId.value = Number.isFinite(n) ? n : null
    }
  } else {
    discussionFocusPostId.value = null
  }
  discussionTarget.value = {
    courseName: item.courseName || '',
    pointName: item.pointName || ''
  }
  discussionVisible.value = true
}
const closeDiscussionPoint = () => {
  discussionVisible.value = false
  discussionTarget.value = null
  discussionFocusPostId.value = null
}

const pointTestVisible = ref(false)
const pointTestTarget = ref(null)
const openPointTest = (item) => {
  if (!item || !canPublishPointTest(item)) return
  pointTestTarget.value = {
    id: item.id,
    courseName: item.courseName || '',
    pointName: item.pointName || '',
    courseRoot: Boolean(item.courseRoot)
  }
  pointTestVisible.value = true
}
const closePointTest = () => {
  pointTestVisible.value = false
  pointTestTarget.value = null
}

const applyTeacherDiscussionDeepLink = async () => {
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

  await refreshTeacherCoursePermissionsIfNeeded(false)
  const opts = Array.isArray(catalogCourses.value) ? catalogCourses.value : []
  const nextQ = { ...route.query }
  delete nextQ.dc
  delete nextQ.dp
  delete nextQ.dpost

  if (!opts.includes(courseName)) {
    discussionFocusPostId.value = null
    try {
      await router.replace({ path: route.path, query: nextQ })
    } catch {
      /* ignore */
    }
    return
  }

  const anchorId = Number.isFinite(postNum) ? postNum : null
  suppressCourseWatch.value = true
  selectedCourse.value = courseName
  pointForm.value.courseName = courseName
  suppressCourseWatch.value = false
  await loadKnowledgePointData()
  openDiscussionPoint({ courseName, pointName }, anchorId)
  try {
    await router.replace({ path: route.path, query: nextQ })
  } catch {
    /* ignore */
  }
}

const userInitial = computed(() => (props.currentUser && props.currentUser.username ? props.currentUser.username.charAt(0).toUpperCase() : '?'))
const selectedCourse = ref(pointForm.value.courseName || '')
const courseInitDone = ref(false)
const suppressCourseWatch = ref(false)
// 当教师端从课程广场手动进入/退出课程后，会禁止刷新权限时的“自动选第一个课程”
const autoSelectCourseEnabled = ref(true)
const coursePermFetchInFlight = ref(false)
const lastCoursePermFetchAt = ref(0)

watch(() => courseOptions.value, () => {
  if (suppressCourseWatch.value) return
  const opts = Array.isArray(courseOptions.value) ? courseOptions.value : []
  if (!opts.length) {
    selectedCourse.value = ''
    pointForm.value.courseName = ''
    return
  }
  if (!opts.includes(selectedCourse.value)) {
    if (autoSelectCourseEnabled.value) {
      selectedCourse.value = opts[0] || ''
    } else {
      selectedCourse.value = ''
    }
    pointForm.value.courseName = selectedCourse.value
  }
})

// 当顶部选择课程改变时，同步到 pointForm 并刷新知识点
watch(selectedCourse, (val) => {
  if (suppressCourseWatch.value) return
  if (!val) {
    pointForm.value.courseName = ''
    points.value = []
    return
  }
  pointForm.value.courseName = val
  if (courseInitDone.value) switchCourse()
})

const refreshTeacherCoursePermissionsIfNeeded = async (force = false) => {
  // 30s 内缓存权限，避免频繁请求；但若当前 selectedCourse 不在权限列表内，则必须刷新
  const now = Date.now()
  const opts = Array.isArray(catalogCourses.value) ? catalogCourses.value : []
  const selectedStillAllowed = opts.includes(selectedCourse.value)
  const shouldFetch = force || !opts.length || !selectedStillAllowed || now - lastCoursePermFetchAt.value > 30000
  if (!shouldFetch) return
  if (coursePermFetchInFlight.value) return

  coursePermFetchInFlight.value = true
  try {
    const res = await listTeacherCoursePermissions(props.currentUser.id)
    const payload = res && res.data ? res.data : res
    const nextCourses = Array.isArray(payload?.courses) ? payload.courses : []

    suppressCourseWatch.value = true
    catalogCourses.value = nextCourses

    if (nextCourses.length) {
      const stillAllowed = Boolean(selectedCourse.value && nextCourses.includes(selectedCourse.value))
      if (stillAllowed) {
        // 保持教师手动进入的课程
        pointForm.value.courseName = selectedCourse.value
      } else if (autoSelectCourseEnabled.value) {
        selectedCourse.value = nextCourses[0] || ''
        pointForm.value.courseName = selectedCourse.value
      } else {
        // 手动“退出课程”状态：权限刷新时保持未进入
        selectedCourse.value = ''
        pointForm.value.courseName = ''
      }
    } else {
      selectedCourse.value = ''
      pointForm.value.courseName = ''
    }
  } catch {
    // 失败则保持当前状态，但会导致后续请求为空；这里不强行清空，避免误伤
  } finally {
    suppressCourseWatch.value = false
    coursePermFetchInFlight.value = false
    lastCoursePermFetchAt.value = Date.now()
  }
}

// 加载数据
const loadBaseData = async () => {
  message.value = ''
  error.value = ''
  try {
    const res = await listMaterials()
    const payload = res && res.data ? res.data : res
    materials.value = Array.isArray(payload) ? payload : (payload && payload.items) ? payload.items : []
    const testCountResp = await countPublishedTestsByTeacherCourses(props.currentUser.id)
    publishedTestCount.value = Number(testCountResp?.data?.publishedTestCount || 0)
  } catch (err) {
    error.value = err?.response?.data?.message || '加载资料失败。'
    publishedTestCount.value = 0
  }
}

const normCourse = (x) => String(x || '').trim()
const authorizedCourseCount = computed(() => {
  const set = new Set((Array.isArray(courseOptions.value) ? courseOptions.value : []).map(normCourse).filter(Boolean))
  return set.size
})
const accessibleMaterialsCount = computed(() => {
  const allowed = new Set((Array.isArray(courseOptions.value) ? courseOptions.value : []).map(normCourse).filter(Boolean))
  if (!allowed.size) return 0
  const list = Array.isArray(materials.value) ? materials.value : []
  return list.filter((m) => allowed.has(normCourse(m?.courseName))).length
})

const loadKnowledgePointData = async () => {
  pointMessage.value = ''
  pointError.value = ''
  try {
    await refreshTeacherCoursePermissionsIfNeeded()
    const courseName = pointForm.value.courseName || selectedCourse.value || ''
    if (!courseName) {
      points.value = []
      selectedPointIds.value = []
      return
    }
    const res = await listKnowledgePoints(courseName, props.currentUser.id)
    const payload = res && res.data ? res.data : res
    const rawPoints = Array.isArray(payload) ? payload : (payload && payload.points) ? payload.points : []
    points.value = Array.isArray(rawPoints) ? rawPoints : []
    if (!analyticsPointName.value) {
      const arr = Array.isArray(points.value) ? points.value : []
      const root = arr.find((p) => p?.courseRoot)
      analyticsPointName.value = root?.pointName || arr[0]?.pointName || ''
    }
    selectedPointIds.value = []
  } catch (err) {
    pointError.value = err?.response?.data?.message || '加载知识点失败。'
  }
}

const openAnalyticsForPoint = (p) => {
  analyticsPointName.value = String(p?.pointName || '').trim()
  // 尽量滚动到分析面板位置
  try {
    requestAnimationFrame(() => {
      const el = document.getElementById('teacher-analytics-panel')
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  } catch {}
}

const loadMaterialsByKnowledgePoint = async (kp) => {
  if (!kp) {
    materials.value = []
    return
  }
  message.value = ''
  error.value = ''
  try {
    await refreshTeacherCoursePermissionsIfNeeded()
    const courseName = pointForm.value.courseName || selectedCourse.value || ''
    if (!courseName) {
      materials.value = []
      return
    }
    const resp = await fetchMaterialsByKnowledgePoint(courseName, kp, false, props.currentUser.id)
    materials.value = resp && resp.data ? resp.data : resp || []
  } catch (err) {
    error.value = err?.response?.data?.message || '加载资料失败。'
    materials.value = []
  }
}

const handleFileChange = (e) => {
  uploadForm.value.files = Array.from(e.target.files || [])
}

const openUploadModal = (point) => {
  uploadTargetPoint.value = point
  uploadForm.value.title = ''
  uploadForm.value.description = ''
  uploadForm.value.files = []
  uploadForm.value.category = uploadForm.value.category || 'ATTACHMENT'
  uploadForm.value.point = point.pointName || point
  uploadForm.value.course = point.courseName || point.course || point.courseName
  uploadDialogVisible.value = true
}

const openViewMaterials = async (point) => {
  viewMaterialsDialogVisible.value = true
  viewMaterialsPoint.value = point.pointName || point
  viewMaterialsList.value = []
  try {
    await refreshTeacherCoursePermissionsIfNeeded()
    const resp = await fetchMaterialsByKnowledgePoint(point.courseName || point.course || selectedCourse.value, viewMaterialsPoint.value, false, props.currentUser.id)
    viewMaterialsList.value = resp && resp.data ? resp.data : resp
  } catch (e) {
    viewMaterialsList.value = []
  }
}

const submitMaterial = async () => {
  message.value = ''
  error.value = ''
  loading.value = true
  try {
    if (!uploadForm.value.title || !uploadForm.value.title.trim()) {
      error.value = '请填写资料标题。'
      loading.value = false
      return
    }
    if (!uploadForm.value.point) {
      error.value = '请先选择具体知识点！'
      loading.value = false
      return
    }
    if (!uploadForm.value.files.length) {
      error.value = '请至少选择一个文件！'
      loading.value = false
      return
    }
    if (!uploadForm.value.course || !String(uploadForm.value.course).trim()) {
      error.value = '课程信息缺失，请重新从知识点列表点击“上传资料”。'
      loading.value = false
      return
    }
    const category = String(uploadForm.value.category || 'ATTACHMENT').toUpperCase()
    for (const file of uploadForm.value.files) {
      const lowerName = String(file?.name || '').toLowerCase()
      if (category === 'DOCUMENT' && !lowerName.endsWith('.pdf')) {
        error.value = `文件「${file.name}」不是 .pdf，文档类只支持 PDF。`
        loading.value = false
        return
      }
      if (category === 'VIDEO' && !lowerName.endsWith('.mp4')) {
        error.value = `文件「${file.name}」不是 .mp4，视频类只支持 MP4。`
        loading.value = false
        return
      }
      const fd = new FormData()
      fd.append('teacherId', String(props.currentUser.id))
      // 直接传字符串字段，避免部分环境下 Blob 文本字段被后端当作文件 part 丢失
      fd.append('title', String(uploadForm.value.title || ''))
      fd.append('description', String(uploadForm.value.description || ''))
      fd.append('courseName', String(uploadForm.value.course || ''))
      fd.append('knowledgePoint', String(uploadForm.value.point || ''))
      fd.append('category', category)
      fd.append('file', file)
      await uploadMaterial(fd)
    }
    uploadForm.value.title = ''
    uploadForm.value.description = ''
    uploadForm.value.files = []
    await loadBaseData()
    // 刷新当前知识点下的资料（后端会包含下级）
    if (uploadForm.value.point) await loadMaterialsByKnowledgePoint(uploadForm.value.point)
    message.value = '资料上传成功。'
    // 关闭上传弹窗
    uploadDialogVisible.value = false
  } catch (err) {
    error.value = err?.response?.data?.message || '资料上传失败。'
  } finally {
    loading.value = false
  }
}

const switchCourse = async () => {
  pointMessage.value = ''
  pointError.value = ''
  await loadKnowledgePointData()
}

watch(() => uploadForm.value.point, (val) => {
  if (val) {
    loadMaterialsByKnowledgePoint(val)
  }
})

const openEditPoint = (point) => {
  const resolvedParentId =
    point?.parentId ??
    (point?.parentPoint
      ? (points.value.find((p) => String(p?.pointName || '').trim() === String(point.parentPoint || '').trim())?.id ?? null)
      : null)
  editingPoint.value = point
  editPointForm.value = {
    ...point,
    parentId: resolvedParentId
  }
  editDialogVisible.value = true
}

const openAddPoint = () => {
  const cn = pointForm.value.courseName || selectedCourse.value || ''
  if (!cn) {
    pointError.value = '暂无可访问课程，请联系管理员分配课程权限。'
    return
  }
  editingPoint.value = null
  editPointForm.value = {
    courseName: cn,
    pointName: '',
    parentId: null,
    parentPoint: ''
  }
  editDialogVisible.value = true
}

const downloadKnowledgePointMdTemplate = async () => {
  const url = '/knowledge-points-template.md'
  const filename = 'knowledge-points-template.md'

  try {
    // 尽量使用 blob 强制触发下载，避免新标签页直接展示
    const resp = await fetch(url, { cache: 'no-store' })
    if (!resp.ok) throw new Error('download template fetch failed')
    const blob = await resp.blob()
    const objectUrl = URL.createObjectURL(blob)

    const a = document.createElement('a')
    a.href = objectUrl
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()

    URL.revokeObjectURL(objectUrl)
  } catch {
    // 兜底：如果 blob 下载失败，则打开链接（浏览器可能仍会下载/或展示）
    window.open(url, '_blank')
  }
}

const parseCourseFromMdHeader = (text) => {
  const lines = String(text || '').split(/\r?\n/)
  for (const raw of lines.slice(0, 20)) {
    const line = String(raw || '').trim()
    if (!line) continue
    // 支持：course: xxx 或 <!-- course: xxx -->
    const m = line.match(/course\s*:\s*([^\-#<>\r\n]+)\s*$/i)
    if (m && m[1]) return m[1].trim()
  }
  return ''
}

const parseKnowledgePointsFromMd = (text, defaultCourseName) => {
  const src = String(text || '')
  // 优先使用教师端“当前选择课程”（来自个人中心统计课程），
  // 避免模板/文档里的 course 字段抢占当前选择。
  const courseName = defaultCourseName || parseCourseFromMdHeader(src) || ''
  const out = []
  const headingStack = [] // { level, pointName, nodeKey }
  let seq = 0
  let inCode = false
  const lines = src.split(/\r?\n/)

  for (const raw of lines) {
    const line = String(raw || '')

    // 跳过代码块内容
    if (/^\s*```/.test(line)) {
      inCode = !inCode
      continue
    }
    if (inCode) continue

    const m = line.match(/^\s*(#{1,6})\s+(.+?)\s*$/)
    if (!m) continue

    const level = m[1].length
    const pointName = m[2]
      .replace(/\s+#+\s*$/, '') // 去掉结尾多余的 ####
      .trim()

    if (!pointName) continue

    // 不把 H1（单个 #）当作知识点节点导入，避免模板/说明标题进入数据库
    if (level <= 1) continue

    while (headingStack.length && headingStack[headingStack.length - 1].level >= level) {
      headingStack.pop()
    }
    const parent = headingStack.length ? headingStack[headingStack.length - 1] : null
    const nodeKey = `md-${++seq}`
    out.push({
      courseName,
      pointName,
      parentPoint: parent ? parent.pointName : null,
      nodeKey,
      parentNodeKey: parent ? parent.nodeKey : null
    })

    headingStack.push({ level, pointName, nodeKey })
  }

  return { courseName, items: out }
}

const openMdImport = () => {
  mdImportError.value = ''
  mdImportResult.value = ''
  mdImportFile.value = null
  mdImportText.value = ''
  mdImportPreview.value = []
  mdImportCourse.value = selectedCourse.value || pointForm.value.courseName || ''

  // 触发系统文件选择器（选择本地 .md 文件）
  mdFileInputRef.value?.open()
}

const onMdFileChange = async (e) => {
  mdImportError.value = ''
  mdImportResult.value = ''
  const file = e?.target?.files?.[0] || null
  mdImportFile.value = file
  mdImportText.value = ''
  mdImportPreview.value = []

  if (!file) return
  const name = String(file.name || '')
  if (!name.toLowerCase().endsWith('.md')) {
    mdImportError.value = '请选择 .md 格式文件。'
    return
  }

  try {
    const text = await file.text()
    mdImportText.value = text
    const parsed = parseKnowledgePointsFromMd(text, selectedCourse.value || pointForm.value.courseName || '')
    mdImportCourse.value = parsed.courseName
    mdImportPreview.value = parsed.items
    if (!mdImportPreview.value.length) {
      mdImportError.value = '未解析到任何知识点。请按 `## / ### / ####` 编写，并避免只用 #（H1）标题。'
      return
    }

    // 一键导入：解析完成后直接写入
    await submitMdImport()
  } catch (err) {
    mdImportError.value = '读取文件失败，请重试。'
  } finally {
    // 允许重复导入同一个文件：清空 input 的值
    if (e?.target) {
      e.target.value = ''
    }
  }
}

const submitMdImport = async () => {
  mdImportError.value = ''
  mdImportResult.value = ''

  const items = Array.isArray(mdImportPreview.value) ? mdImportPreview.value : []
  const courseName = selectedCourse.value || mdImportCourse.value || pointForm.value.courseName || ''
  if (!courseName) {
    mdImportError.value = '课程名为空，请在模板中填写 course 或在页面选择课程。'
    return
  }
  if (!items.length) {
    mdImportError.value = '没有可导入的知识点。'
    return
  }

  mdImportLoading.value = true
  let ok = 0
  let fail = 0
  const failSamples = []

  try {
    // 逐条导入（按 MD 顺序），用 parentId 精确建立层级，彻底规避“重名父节点串层级”。
    const savedIdByNodeKey = new Map()
    for (const it of items) {
      try {
        const parentId = it.parentNodeKey ? savedIdByNodeKey.get(it.parentNodeKey) ?? null : null
        const resp = await saveKnowledgePoint({
          courseName,
          pointName: String(it.pointName || '').trim(),
          parentId,
          // 兼容后端旧字段：即便未来只用 parentId，这里保留 parentPoint 不影响正确性
          parentPoint: it.parentPoint ? String(it.parentPoint).trim() : null,
        })
        const savedId = resp?.data?.point?.id
        if (it.nodeKey && savedId != null) {
          savedIdByNodeKey.set(it.nodeKey, Number(savedId))
        }
        ok++
      } catch (e) {
        fail++
        if (failSamples.length < 5) {
          const msg = e?.response?.data?.message || e?.message || '导入失败'
          failSamples.push(`${it.pointName}: ${msg}`)
        }
      }
    }

    await loadKnowledgePointData()
    mdImportResult.value =
      `导入完成：成功 ${ok} 条，失败 ${fail} 条。` +
      (failSamples.length ? `（示例：${failSamples.join('；')}）` : '')
  } finally {
    mdImportLoading.value = false
  }
}

const handleUpdatePoint = async () => {
  pointMessage.value = ''
  pointError.value = ''
  try {
    const payload = {
      courseName: editPointForm.value.courseName,
      pointName: (editPointForm.value.pointName || '').trim(),
      parentId: editPointForm.value.parentId == null ? null : Number(editPointForm.value.parentId),
      sortOrder: Number(editPointForm.value.sortOrder) || 0,
    }

    if (!payload.pointName) {
      pointError.value = '知识点名称不能为空。'
      return
    }

    await updateKnowledgePoint(editingPoint.value.id, payload)
    // 确保重新加载使用正确课程（避免表格看起来没更新）
    pointForm.value.courseName = payload.courseName
    await loadKnowledgePointData()
    editDialogVisible.value = false
    pointMessage.value = '知识点已更新。'
  } catch (err) {
    pointError.value = err?.response?.data?.message || '知识点更新失败。'
  }
}

const handleCreatePoint = async () => {
  pointMessage.value = ''
  pointError.value = ''
  if (!editPointForm.value.pointName || !editPointForm.value.pointName.trim()) {
    pointError.value = '知识点名称不能为空。'
    return
  }
  try {
    const payload = {
      courseName: editPointForm.value.courseName,
      pointName: editPointForm.value.pointName.trim(),
      parentId: editPointForm.value.parentId == null ? null : Number(editPointForm.value.parentId),
    }
    await saveKnowledgePoint(payload)
    pointForm.value.courseName = payload.courseName
    await loadKnowledgePointData()
    editDialogVisible.value = false
    pointMessage.value = '知识点已创建。'
  } catch (err) {
    pointError.value = err?.response?.data?.message || '知识点创建失败。'
  }
}

const handleDeletePoint = async (id, item) => {
  if (item && isCourseRootPoint(item)) {
    pointError.value = '课程根知识点不可删除。'
    return
  }
  if (!confirm('确定要删除该知识点及其所有下级知识点吗？')) return
  try {
    await deleteKnowledgePoint(id)
    await loadKnowledgePointData()
    pointMessage.value = '知识点及其下级已删除。'
    selectedPointIds.value = []
  } catch (err) {
    pointError.value = err?.response?.data?.message || '知识点删除失败。'
  }
}

const handleDeleteSelectedPoints = async () => {
  const ids = Array.isArray(selectedPointIds.value) ? Array.from(new Set(selectedPointIds.value)) : []
  if (!ids.length) return

  const ok = confirm(`确定要删除选中的 ${ids.length} 个知识点及其所有下级知识点吗？`)
  if (!ok) return

  try {
    // 顺序删除：避免并发触发“父子前置关系”清理时的竞态
    for (const id of ids) {
      // 课程根理论上不会出现在 selectedPointIds，但这里仍做兜底
      const item = points.value.find((p) => p.id === id)
      if (item && isCourseRootPoint(item)) continue
      await deleteKnowledgePoint(id)
    }
    await loadKnowledgePointData()
    selectedPointIds.value = []
    pointMessage.value = '已删除选中的知识点。'
  } catch (err) {
    pointError.value = err?.response?.data?.message || '批量删除失败。'
  }
}

const handleDeleteMaterial = async (id) => {
  if (!confirm('确定要删除该资料吗？')) return
  try {
    await deleteMaterial(id)
    await loadBaseData()
    message.value = '资料已删除。'
  } catch (err) {
    error.value = err?.response?.data?.message || '资料删除失败。'
  }
}

const loadTeacherProfile = () => {
  // 按教师账号隔离缓存，避免同一浏览器切换账号后学院串号
  const legacyKey = 'teacher_college'
  const key = `teacher_college_${props.currentUser?.id ?? ''}`
  const storedCollege = localStorage.getItem(key) || ''
  if (storedCollege) {
    selectedCollege.value = storedCollege
    return
  }
  // 兼容旧版本：如果存在老的全局 key，则迁移一次到当前账号 key（迁移后清理旧 key）
  const legacy = localStorage.getItem(legacyKey) || ''
  if (legacy) {
    selectedCollege.value = legacy
    try {
      localStorage.setItem(key, legacy)
      localStorage.removeItem(legacyKey)
    } catch (e) {}
    return
  }
  // 兜底：如果本地没有缓存，但 currentUser 上有 college 字段，则用于初始化
  if (props.currentUser && props.currentUser.college) {
    selectedCollege.value = props.currentUser.college
  } else {
    selectedCollege.value = ''
  }
}

// 编辑资料 / 修改密码 处理
const openEditProfile = () => {
  editProfileForm.value.username = profileForm.value.username || props.currentUser.username || ''
  editProfileForm.value.email = profileForm.value.email || props.currentUser.email || ''
  editProfileForm.value.college = selectedCollege.value || ''
  editProfileVisible.value = true
}

const handleSaveProfile = async () => {
  profileForm.value.username = editProfileForm.value.username
  profileForm.value.email = editProfileForm.value.email
  // 更新学院到 local state
  selectedCollege.value = editProfileForm.value.college || ''
  // 立刻持久化到本地存储（按账号隔离），保证刷新页面后学院能正确回显
  try {
    const key = `teacher_college_${props.currentUser?.id ?? ''}`
    localStorage.setItem(key, selectedCollege.value || '')
    // 清理旧 key，避免串号
    localStorage.removeItem('teacher_college')
  } catch (e) {}
  // 尝试同步基础用户信息到服务器（后端目前仅支持用户名/邮箱）
  try {
    const payload = {
      userId: props.currentUser.id,
      username: profileForm.value.username,
      email: profileForm.value.email,
      college: selectedCollege.value || ''
    }
    const resp = await updateUser(payload)
    const updatedUser = resp?.data?.user
      ? resp.data.user
      : { ...props.currentUser, username: profileForm.value.username, email: profileForm.value.email }
    // 在 currentUser 本地副本中同时记录 college，方便其他地方使用
    const enrichedUser = { ...updatedUser, college: selectedCollege.value || '' }
    try {
      localStorage.setItem('currentUser', JSON.stringify(enrichedUser))
    } catch (e) {}
    emit('update-user', {
      username: enrichedUser.username,
      email: enrichedUser.email,
      college: enrichedUser.college,
      ...(enrichedUser.workId !== undefined && enrichedUser.workId !== null ? { workId: enrichedUser.workId } : {})
    })
    profileMessage.value = resp?.data?.message || '已更新用户信息'
  } catch (err) {
    // 后端同步失败也在本地生效
    const fallbackUser = {
      ...props.currentUser,
      username: profileForm.value.username,
      email: profileForm.value.email,
      college: selectedCollege.value || ''
    }
    try {
      localStorage.setItem('currentUser', JSON.stringify(fallbackUser))
    } catch (e) {}
    emit('update-user', {
      username: fallbackUser.username,
      email: fallbackUser.email,
      college: fallbackUser.college,
      ...(fallbackUser.workId ? { workId: fallbackUser.workId } : {})
    })
    profileMessage.value = err?.response?.data?.message || '已更新本地信息（未同步服务器）'
  } finally {
    editProfileVisible.value = false
    setTimeout(() => (profileMessage.value = ''), 2500)
  }
}

const openPasswordPage = () => {
  changePasswordVisible.value = true
}


onMounted(async () => {
  try {
    // 初始化“是否已进入课程”偏好：如果本地已保存过进入/退出操作，则禁止自动选第一个课程
    const storageKey = `teacher-entered-course:${props.currentUser?.id}`
    try {
      const stored = localStorage.getItem(storageKey)
      if (stored !== null) {
        autoSelectCourseEnabled.value = false
        selectedCourse.value = stored
        pointForm.value.courseName = stored
      }
    } catch {
      // ignore localStorage failures
    }

    await loadBaseData()
    await loadMyCourseCatalog()
    // 初始化：拉取管理员分配给当前教师的可见课程
    await refreshTeacherCoursePermissionsIfNeeded(true)

    // 若从课程广场显式携带了 course 参数，则以该参数为准；
    // 同时清空讨论深链参数，避免后续 deepLink 抢占 selectedCourse。
    const incomingCourse = route.query.course ? String(route.query.course).trim() : ''
    if (incomingCourse) {
      const opts = Array.isArray(catalogCourses.value) ? catalogCourses.value : []
      if (opts.includes(incomingCourse)) {
        autoSelectCourseEnabled.value = false
        suppressCourseWatch.value = true
        selectedCourse.value = incomingCourse
        pointForm.value.courseName = incomingCourse
        suppressCourseWatch.value = false
      }
      // 清理深链参数，避免组件挂载后被 deepLink 覆盖
      try {
        const nextQ = { ...route.query }
        delete nextQ.course
        delete nextQ.dc
        delete nextQ.dp
        delete nextQ.dpost
        await router.replace({ path: route.path, query: nextQ })
      } catch (e) {
        // ignore
      }
    }

    await loadKnowledgePointData()
    courseInitDone.value = true
    loadTeacherProfile()
    // 若直接进入“课程广场”，则在页面初次渲染后加载市场/申请数据
    if (currentPage.value === 'courses') {
      await loadTeacherCourseMarket()
    }
    // 若已携带显式 course 覆盖，则不再应用讨论深链
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

watch(
  () => route.query.course,
  (c) => {
    if (currentPage.value !== 'course-detail') return
    const cn = Array.isArray(c) ? c[0] : c
    void loadCourseDetail(cn)
  }
)
</script>

<template>
  <TeacherViewMaterialsModal
    :visible="viewMaterialsDialogVisible"
    :point-name="viewMaterialsPoint"
    :materials="viewMaterialsList"
    @close="viewMaterialsDialogVisible = false"
    @delete-material="handleDeleteMaterial"
  />
    <section class="panel-stack teacher-theme">
      <template v-if="currentPage === 'profile'">
      <article class="result-card profile-hero-card">
        <div class="profile-hero-main">
          <div class="profile-avatar">{{ userInitial }}</div>
          <div>
            <h3>{{ profileForm.username || currentUser.username }}</h3>
          </div>
        </div>
      </article>

      <div class="profile-grid">
        <article class="result-card profile-overview-card">
          <h3>教学统计</h3>
          <div class="profile-stat-list">
            <div>
              <span>已授权课程</span>
              <strong>{{ authorizedCourseCount }}</strong>
            </div>
            <div>
              <span>已上传资料</span>
              <strong>{{ accessibleMaterialsCount }}</strong>
            </div>
            <div>
              <span>已发布测试</span>
              <strong>{{ publishedTestCount }}</strong>
            </div>
          </div>
        </article>

        <article class="result-card profile-detail-card">
          <h3>资料设置</h3>
          <div class="grid-form">
              <label>
                用户名
                <div class="panel-subtitle">{{ profileForm.username || currentUser.username }}</div>
              </label>
              <label>
                学工号
                <div class="panel-subtitle">{{ currentUser.workId || '未设置' }}</div>
              </label>
              <label>
                邮箱
                <div class="panel-subtitle">{{ profileForm.email || currentUser.email }}</div>
              </label>
              <label>
                学院
                <div class="panel-subtitle">{{ (colleges.find(c => c.code === selectedCollege) || {}).name || '未设置' }}</div>
              </label>
          </div>
          <div class="profile-btn-row">
            <button type="button" class="nav-btn" @click="openEditProfile">编辑资料</button>
            <button type="button" class="nav-btn" @click="openPasswordPage">修改密码</button>
            
            <button type="button" class="danger-btn profile-logout-btn" @click="emit('logout')">退出登录</button>
          </div>
          <p v-if="profileMessage" class="ok-text">{{ profileMessage }}</p>
        </article>
      </div>
      </template>

      <template v-else-if="currentPage === 'courses'">
        <TeacherCourses
          :my-course-catalog="myCourseCatalog"
          :pending-permission-requests="pendingTeacherCoursePermissionRequestsList"
          :course-init-done="courseInitDone"
          @enter-course="openCourseDetailFromMarket"
          @quit-course="quitCourseFromMarket"
        />
      </template>

      <template v-else-if="currentPage === 'course-detail'">
        <CourseDetailPanel
          role="teacher"
          :detail="courseDetail || { courseName: '', coverUrl: '', summary: '', syllabus: '' }"
          :loading="courseDetailLoading"
          :error="courseDetailError"
          :can-access="Boolean(courseDetail?.hasAccess)"
          :can-edit-meta="Boolean(courseDetail?.canEditMeta)"
          :is-submitting="courseMetaSaving"
          :edit-form="courseMetaForm"
          @update:edit-form="(v) => (courseMetaForm = v)"
          @enter="() => { if (courseDetail?.courseName) enterCourseFromMarket(courseDetail.courseName) }"
          @apply="() => openPermissionRequest(courseDetail?.courseName)"
          @join="() => null"
          @save-meta="saveCourseMeta"
          @upload-cover="onCourseCoverFileChange"
        />
      </template>

      <template v-else-if="currentPage === 'manage'">
        <TeacherKnowledge
          :points="points"
          :selected-point-ids="selectedPointIds"
          :point-message="pointMessage"
          :point-error="pointError"
          :md-import-loading="mdImportLoading"
          :md-import-error="mdImportError"
          :md-import-result="mdImportResult"
          :get-point-number="getPointNumber"
          @update:selected-point-ids="selectedPointIds = $event"
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

        <div id="teacher-analytics-panel" class="ui-mt-12">
          <TeacherStudentAnalytics
            :current-user="currentUser"
            :selected-course="selectedCourse"
            :points="points"
            :point-name="analyticsPointName"
            :get-point-number="getPointNumber"
            @update:point-name="(v) => (analyticsPointName = v)"
          />
        </div>
    </template>
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
          <h3>知识点交流区 — {{ discussionTarget.pointName }}</h3>
          <p class="panel-subtitle" style="margin-top: 4px">课程：{{ discussionTarget.courseName }}</p>
          <KnowledgePointDiscussion
            :course-name="discussionTarget.courseName"
            :point-name="discussionTarget.pointName"
            :current-user-id="currentUser?.id"
            :user-role="currentUser?.role"
            :focus-post-id="discussionFocusPostId"
            :disabled="false"
          />
        </div>
      </div>
    </div>

    <AiAssistantWidget role="teacher" :current-user="currentUser" />
</template>

<style src="./teacher-portal.css"></style>
