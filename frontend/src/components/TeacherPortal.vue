<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import colleges from '../data/colleges.json'
import { listKnowledgePoints, listMaterials, saveKnowledgePoint, uploadMaterial, updateUser, listCoursesByMajor, fetchMaterialsByKnowledgePoint, fetchAnnouncements } from '../api/client'
import { deleteKnowledgePoint, updateKnowledgePoint, deleteMaterial } from '../api/point-material-ops'
import AccountSecurityPanel from './AccountSecurityPanel.vue'

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
const emit = defineEmits(['logout', 'update-user'])
const route = useRoute()
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

// 上传表单与状态
const uploadForm = ref({ title: '', description: '', course: '', chapter: '', section: '', point: '', files: [] })
const loading = ref(false)
const message = ref('')
const error = ref('')
const uploadDialogVisible = ref(false)
const uploadTargetPoint = ref(null)
const viewMaterialsDialogVisible = ref(false)
const viewMaterialsList = ref([])
const viewMaterialsPoint = ref('')

// 知识点表单
const pointForm = ref({ courseName: '高等数学' })
const pointMessage = ref('')
const pointError = ref('')

// 编辑弹窗
const editingPoint = ref(null)
const editDialogVisible = ref(false)
const editPointForm = ref({ courseName: '', pointName: '', parentPoint: '' })

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

const courseOptions = computed(() => {
  if (Array.isArray(catalogCourses.value) && catalogCourses.value.length) {
    return catalogCourses.value
  }
  const set = new Set()
  const pts = Array.isArray(points.value) ? points.value : []
  const mats = Array.isArray(materials.value) ? materials.value : []
  pts.forEach(p => p && p.courseName && set.add(p.courseName))
  mats.forEach(m => m && m.courseName && set.add(m.courseName))
  return Array.from(set.size ? set : ['高等数学'])
})

const profileMessage = ref('')
const selectedCollege = ref('')

const announcements = ref([])
const annLoading = ref(false)
const annError = ref('')

const loadTeacherAnnouncements = async () => {
  annLoading.value = true
  annError.value = ''
  try {
    const { data } = await fetchAnnouncements()
    announcements.value = Array.isArray(data) ? data : []
  } catch (e) {
    annError.value = e?.response?.data?.message || '加载公告失败。'
    announcements.value = []
  } finally {
    annLoading.value = false
  }
}

const formatAnnTime = (iso) => {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return String(iso)
  }
}

watch(
  currentPage,
  (v) => {
    if (v === 'announcements') void loadTeacherAnnouncements()
  },
  { immediate: true }
)
 
// 与学生端保持一致的个人信息编辑/修改密码逻辑（简化）
const profileForm = ref({ username: props.currentUser.username || '', email: props.currentUser.email || '' })
const editProfileVisible = ref(false)
const editProfileForm = ref({ username: '', email: '', college: '' })
const changePasswordVisible = ref(false)
const passwordPanelRef = ref(null)

const userInitial = computed(() => (props.currentUser && props.currentUser.username ? props.currentUser.username.charAt(0).toUpperCase() : '?'))
const selectedCourse = ref(pointForm.value.courseName || '')

watch(() => courseOptions.value, () => {
  if (!selectedCourse.value) selectedCourse.value = courseOptions.value[0] || pointForm.value.courseName || ''
})

// 当顶部选择课程改变时，同步到 pointForm 并刷新知识点
watch(selectedCourse, (val) => {
  if (val) {
    pointForm.value.courseName = val
    switchCourse()
  }
})

// 加载数据
const loadBaseData = async () => {
  message.value = ''
  error.value = ''
  try {
    const res = await listMaterials()
    const payload = res && res.data ? res.data : res
    materials.value = Array.isArray(payload) ? payload : (payload && payload.items) ? payload.items : []
  } catch (err) {
    error.value = err?.response?.data?.message || '加载资料失败。'
  }
}

const loadKnowledgePointData = async () => {
  pointMessage.value = ''
  pointError.value = ''
  try {
    const courseName = pointForm.value.courseName || selectedCourse.value || ''
    const res = await listKnowledgePoints(courseName)
    const payload = res && res.data ? res.data : res
    const rawPoints = Array.isArray(payload) ? payload : (payload && payload.points) ? payload.points : []
    points.value = Array.isArray(rawPoints) ? rawPoints : []
  } catch (err) {
    pointError.value = err?.response?.data?.message || '加载知识点失败。'
  }
}

const loadMaterialsByKnowledgePoint = async (kp) => {
  if (!kp) {
    materials.value = []
    return
  }
  message.value = ''
  error.value = ''
  try {
    const courseName = pointForm.value.courseName || selectedCourse.value || ''
    const resp = await fetchMaterialsByKnowledgePoint(courseName, kp, false)
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
  uploadForm.value.point = point.pointName || point
  uploadForm.value.course = point.courseName || point.course || point.courseName
  uploadDialogVisible.value = true
}

const openViewMaterials = async (point) => {
  viewMaterialsDialogVisible.value = true
  viewMaterialsPoint.value = point.pointName || point
  viewMaterialsList.value = []
  try {
    const resp = await fetchMaterialsByKnowledgePoint(point.courseName || point.course || selectedCourse.value, viewMaterialsPoint.value, false)
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
    for (const file of uploadForm.value.files) {
      const fd = new FormData()
      fd.append('teacherId', String(props.currentUser.id))
      // append text fields as UTF-8 blobs to avoid multipart charset issues
      fd.append('title', new Blob([uploadForm.value.title || ''], { type: 'text/plain;charset=UTF-8' }))
      fd.append('description', new Blob([uploadForm.value.description || ''], { type: 'text/plain;charset=UTF-8' }))
      fd.append('knowledgePoint', new Blob([uploadForm.value.point || ''], { type: 'text/plain;charset=UTF-8' }))
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
  editingPoint.value = point
  editPointForm.value = {
    ...point
  }
  editDialogVisible.value = true
}

const openAddPoint = () => {
  editingPoint.value = null
  editPointForm.value = {
    courseName: pointForm.value.courseName || '高等数学',
    pointName: '',
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
  const seen = new Set()

  const headingStack = [] // { level, pointName }
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
    const parentPoint = headingStack.length ? headingStack[headingStack.length - 1].pointName : null

    const key = `${courseName}||${pointName}||${parentPoint || ''}`
    if (!seen.has(key)) {
      seen.add(key)
      out.push({ courseName, pointName, parentPoint })
    }

    headingStack.push({ level, pointName })
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
  mdFileInputRef.value?.click()
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
    // 逐条导入，避免瞬时并发过大；后续如需可改小批量并发
    for (const it of items) {
      try {
        await saveKnowledgePoint({
          courseName,
          pointName: String(it.pointName || '').trim(),
          parentPoint: it.parentPoint ? String(it.parentPoint).trim() : null,
        })
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
      parentPoint: editPointForm.value.parentPoint || null,
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
      parentPoint: editPointForm.value.parentPoint || null,
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
  if (!confirm('确定要删除该知识点吗？')) return
  try {
    await deleteKnowledgePoint(id)
    await loadKnowledgePointData()
    pointMessage.value = '知识点已删除。'
  } catch (err) {
    pointError.value = err?.response?.data?.message || '知识点删除失败。'
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
  // 优先使用本地缓存的教师端学院编码
  const storedCollege = localStorage.getItem('teacher_college') || ''
  if (storedCollege) {
    selectedCollege.value = storedCollege
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
  // 立刻持久化到本地存储，保证刷新页面后学院能正确回显
  try {
    localStorage.setItem('teacher_college', selectedCollege.value || '')
  } catch (e) {}
  // 尝试同步基础用户信息到服务器（后端目前仅支持用户名/邮箱）
  try {
    const payload = { userId: props.currentUser.id, username: profileForm.value.username, email: profileForm.value.email }
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

const handlePasswordSave = async () => {
  if (!passwordPanelRef.value || !passwordPanelRef.value.submitChange) return
  const ok = await passwordPanelRef.value.submitChange()
  if (ok) changePasswordVisible.value = false
}

onMounted(async () => {
  try {
    await loadBaseData()
    await loadKnowledgePointData()
    // 从后端检索课程目录（与学生端一致）
    try {
      const res = await listCoursesByMajor()
      const payload = res && res.data ? res.data : res
      catalogCourses.value = Array.isArray(payload) ? payload : []
    } catch (e) {
      catalogCourses.value = []
    }
    loadTeacherProfile()
  } catch (e) {
    error.value = '加载教师端数据失败，请确认后端与数据库已启动。'
  }
})
</script>

<template>
  <section class="panel">
    <div class="panel-header">
      <h2>教师端 · 教学资源空间</h2>
    </div>
    <!-- 查看已上传资料模态框 -->
    <div v-if="viewMaterialsDialogVisible" class="modal-mask" @click.self="viewMaterialsDialogVisible = false">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" @click="viewMaterialsDialogVisible = false" aria-label="关闭">×</button>
          <h3>已上传资料 - {{ viewMaterialsPoint }}</h3>
          <div v-if="!viewMaterialsList.length" class="panel-subtitle">暂无资料。</div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>标题</th>
                <th>描述</th>
                <th>文件名</th>
                <th>上传者</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in viewMaterialsList" :key="m.id">
                <td>{{ m.title }}</td>
                <td>{{ m.description || '-' }}</td>
                <td>{{ m.fileName || '-' }}</td>
                <td>{{ m.teacherName || '-' }}</td>
                <td>{{ m.createdAt ? new Date(m.createdAt).toLocaleString() : '-' }}</td>
                <td>
                  <button @click="handleDeleteMaterial(m.id)" style="color:red;">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
    <section class="panel-stack">
      <template v-if="currentPage === 'profile'">
      <article class="result-card profile-hero-card">
        <div class="profile-hero-main">
          <div class="profile-avatar">{{ userInitial }}</div>
          <div>
            <h3>{{ profileForm.username || currentUser.username }}</h3>
          </div>
        </div>
        <div class="profile-hero-actions">
          <div class="grid-form">
              <label>
                统计课程
                <select v-model="selectedCourse">
                  <option v-for="course in courseOptions" :key="course" :value="course">{{ course }}</option>
                </select>
              </label>
          </div>
        </div>
      </article>

      <div class="profile-grid">
        <article class="result-card profile-overview-card">
          <h3>教学统计</h3>
          <div class="profile-stat-list">
            <div>
              <span>已上传资料</span>
              <strong>{{ materials.length }}</strong>
            </div>
            <div>
              <span>知识点总数</span>
              <strong>{{ points.length }}</strong>
            </div>
            <div>
              <span>当前课程</span>
              <strong>{{ selectedCourse || pointForm.courseName }}</strong>
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
            
            <button class="danger-btn profile-logout-btn" @click="emit('logout')">退出登录</button>
          </div>
          <p v-if="profileMessage" class="ok-text">{{ profileMessage }}</p>
        </article>
      </div>
      </template>

      <template v-else-if="currentPage === 'announcements'">
        <article class="result-card">
          <h3>平台公告</h3>
          <p v-if="annLoading && !announcements.length" class="panel-subtitle">加载中…</p>
          <p v-else-if="annError" class="error-text">{{ annError }}</p>
          <p v-else-if="!announcements.length" class="panel-subtitle">暂无公告。</p>
          <div v-else class="announcement-read-list">
            <article v-for="a in announcements" :key="a.id" class="result-card announcement-read-card">
              <h4 class="announcement-read-title">{{ a.title }}</h4>
              <p class="panel-subtitle announcement-read-meta">
                <span v-if="a.publisherName">{{ a.publisherName }}</span>
                <span v-if="a.publisherName && a.createdAt"> · </span>
                <span>{{ formatAnnTime(a.createdAt) }}</span>
              </p>
              <div class="announcement-read-body">{{ a.content }}</div>
            </article>
          </div>
        </article>
      </template>

      <template v-else-if="currentPage === 'manage'">
      <!-- 单条知识点上传功能已移至表格内的“上传资料”按钮 -->

      <article class="result-card">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
          <h3>课程知识点设置</h3>
          <div style="display:flex;gap:8px;align-items:center;">
            <button type="button" class="cancel-button" @click="downloadKnowledgePointMdTemplate">下载 MD 模板</button>
            <button type="button" class="cancel-button" @click="openMdImport">导入 MD</button>
            <button type="button" class="match-button" @click="openAddPoint">新增知识点</button>
          </div>
        </div>
        <div class="grid-form four-col">
          <label>
            <!-- 占位，保持四列布局 -->
          </label>
          <label>
            <!-- 占位，保持四列布局 -->
          </label>
          <label>
            <!-- 占位，保持四列布局 -->
          </label>
          <label>
            <!-- 占位，保持四列布局 -->
          </label>
        </div>
        <p v-if="pointMessage" class="ok-text">{{ pointMessage }}</p>
        <p v-if="pointError" class="error-text">{{ pointError }}</p>
        <p v-if="mdImportLoading" class="ok-text">导入中，请稍候...</p>
        <p v-if="mdImportError" class="error-text">{{ mdImportError }}</p>
        <p v-if="mdImportResult" class="ok-text">{{ mdImportResult }}</p>

        <table class="data-table">
          <thead>
            <tr>
              <th>课程</th>
              <th>知识点</th>
              <th>父级知识点</th>
              <!-- 顺序 列已移除 -->
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in points" :key="item.id">
              <td>{{ item.courseName }}</td>
              <td>{{ item.pointName }}<span v-if="isCourseRootPoint(item)" class="panel-subtitle" style="margin-left:6px">（课程根）</span></td>
              <td>{{ item.parentPoint || '-' }}</td>
              <!-- 顺序 值已移除 -->
              <td>
                <button v-if="!isCourseRootPoint(item)" type="button" @click="openEditPoint(item)">编辑</button>
                <button @click="openUploadModal(item)" style="margin-left:8px;">上传资料</button>
                <button @click="openViewMaterials(item)" style="margin-left:8px;">查看资料</button>
                <button
                  v-if="!isCourseRootPoint(item)"
                  type="button"
                  @click="handleDeletePoint(item.id, item)"
                  style="margin-left:8px;color:red;"
                >删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </article>

      <!-- 已上传资料表格已移除；教师可在知识点行内上传，学生端会展示相应资料 -->
    </template>
    </section>
      <div v-if="editDialogVisible" class="modal-mask" @click.self="editDialogVisible = false">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" @click="editDialogVisible = false" aria-label="关闭">×</button>
          <h3>{{ editingPoint ? '编辑知识点' : '新增知识点' }}</h3>
          <div class="grid-form single-col">
            <label>
              知识点名称
              <input v-model="editPointForm.pointName" class="match-height" />
            </label>
            <label>
              父节点（层级关系）
              <select v-model="editPointForm.parentPoint" class="match-height">
                <option value="">无</option>
                <option v-for="point in points" :key="point.id" :value="point.pointName">{{ point.pointName }}</option>
              </select>
            </label>
          </div>
          <div class="inline-form">
            <!-- 顺序 输入已移除 -->
            <div class="button-row">
              <button class="match-height match-button" @click="editingPoint ? handleUpdatePoint() : handleCreatePoint()">保存</button>
              <button class="match-height cancel-button" @click="editDialogVisible = false" style="margin-left:8px;">取消</button>
            </div>
          </div>
        </div>
      </div>
    </div>

        <!-- 单条上传资料模态框 -->
        <div v-if="uploadDialogVisible" class="modal-mask" @click.self="uploadDialogVisible = false">
          <div class="modal-wrapper">
            <div class="modal-container">
              <button class="modal-close" @click="uploadDialogVisible = false" aria-label="关闭">×</button>
              <h3>上传资料 - {{ uploadForm.point || (uploadTargetPoint && uploadTargetPoint.pointName) }}</h3>
              <div class="grid-form single-col" style="margin-top:12px;">
                <label>
                  资料标题
                  <input v-model="uploadForm.title" class="match-height" placeholder="例如：讲义" />
                </label>
                <label>
                  描述
                  <textarea v-model="uploadForm.description" rows="3" placeholder="输入资料说明"></textarea>
                </label>
                <label>
                  文件（可多选）
                  <input type="file" multiple @change="handleFileChange" />
                </label>
              </div>
              <div style="display:flex;gap:8px;margin-top:12px;">
                <button class="match-height match-button" :disabled="loading || !uploadForm.files.length || !uploadForm.title" @click="submitMaterial">上传</button>
                <button class="match-height cancel-button" @click="uploadDialogVisible = false">取消</button>
              </div>
              <p v-if="message" class="ok-text">{{ message }}</p>
              <p v-if="error" class="error-text">{{ error }}</p>
            </div>
          </div>
        </div>

        <!-- MD 导入：使用系统文件选择器（无弹窗） -->
        <input
          ref="mdFileInputRef"
          type="file"
          accept=".md,text/markdown"
          style="display:none"
          @change="onMdFileChange"
        />

        <!-- 编辑个人资料模态框（页面根级） -->
    <div v-if="editProfileVisible" class="modal-mask" @click.self="editProfileVisible = false">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" @click="editProfileVisible = false" aria-label="关闭">×</button>
          <h3>编辑个人资料</h3>
          <div class="grid-form single-col" style="margin-top:12px;">
            <label>
              用户名
              <input v-model="editProfileForm.username" class="match-height" />
            </label>
            <label>
              邮箱
              <input v-model="editProfileForm.email" type="email" class="match-height" />
            </label>
            <label>
              学院
              <select v-model="editProfileForm.college" class="match-height">
                <option value="">请选择学院</option>
                <option v-for="c in colleges" :key="c.code" :value="c.code">{{ c.name }}</option>
              </select>
            </label>
          </div>
          <div style="display:flex;gap:8px;margin-top:12px;">
            <button class="match-height match-button" @click="handleSaveProfile">保存</button>
            <button class="match-height cancel-button" @click="editProfileVisible = false">取消</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改密码模态框（页面根级） -->
    <div v-if="changePasswordVisible" class="modal-mask" @click.self="changePasswordVisible = false">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" @click="changePasswordVisible = false" aria-label="关闭">×</button>
          <h3>修改密码</h3>
          <div style="margin-top:12px;">
            <AccountSecurityPanel ref="passwordPanelRef" :current-user="currentUser" :embedded="true" />
          </div>
          <div style="display:flex;gap:8px;margin-top:12px;">
            <button class="match-height match-button" @click="handlePasswordSave">保存</button>
            <button class="match-height cancel-button" @click="changePasswordVisible = false">取消</button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped src="./teacher-portal.css"></style>
