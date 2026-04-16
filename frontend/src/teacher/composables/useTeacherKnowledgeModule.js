import { computed, ref, watch } from 'vue'
import {
  listKnowledgePoints,
  listMaterials,
  saveKnowledgePoint,
  uploadMaterial,
  fetchMaterialsByKnowledgePoint,
  listTeacherCoursePermissions,
  countPublishedTestsByTeacherCourses
} from '../../api/client'
import { deleteKnowledgePoint, updateKnowledgePoint, deleteMaterial } from '../../api/point-material-ops'

export function useTeacherKnowledgeModule({ props, route, router, currentPage, myCourseCatalogRef }) {
  const myCourseCatalog = myCourseCatalogRef
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
const publishedTestCount = ref(0)
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

/** 是否具备该课的授课权限：与 /teacher-course-permissions 或课程广场 hasAccess 列表一致即可进入管理页 */
const teacherHasCoursePermission = (rawName) => {
  const cn = normCourse(rawName)
  if (!cn) return false
  const opts = Array.isArray(catalogCourses.value) ? catalogCourses.value : []
  if (opts.includes(cn)) return true
  const cat = Array.isArray(myCourseCatalog.value) ? myCourseCatalog.value : []
  return cat.some((row) => normCourse(row?.courseName) === cn)
}
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
      const el = document.getElementById('teacher-student-analytics-panel')
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
  uploadForm.value.course =
    point.courseName || point.course || selectedCourse.value || pointForm.value.courseName || ''
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
    const refreshPoint = String(uploadForm.value.point || '').trim()

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
    // 刷新当前知识点下的资料（须在清空 uploadForm 之前保存 point/course）
    if (refreshPoint) await loadMaterialsByKnowledgePoint(refreshPoint)
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
