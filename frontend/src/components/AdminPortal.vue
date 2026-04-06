<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  assignTeacherCourses,
  addCourse,
  bulkImportUsers,
  createAnnouncement,
  deleteAnnouncement,
  fetchAnnouncements,
  listCoursesByMajor,
  listTeacherCoursePermissions,
  listTeacherCoursePermissionRequests,
  decideTeacherCoursePermissionRequest,
  deleteCourse,
  listUsers,
  updateUser
} from '../api/client'
import AccountSecurityPanel from './AccountSecurityPanel.vue'
import AiAssistantWidget from './AiAssistantWidget.vue'
import colleges from '../data/colleges.json'

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

const currentPage = ref(route.params.page || props.activePage || 'profile')
watch(() => route.params.page, (v) => {
  currentPage.value = v || props.activePage || 'profile'
})

const profileForm = ref({
  username: props.currentUser.username || '',
  email: props.currentUser.email || '',
  workId: props.currentUser.workId || ''
})
const profileMessage = ref('')
const editProfileVisible = ref(false)
const editProfileForm = ref({ username: '', email: '', workId: '' })
const changePasswordVisible = ref(false)
const passwordPanelRef = ref(null)

const userInitial = computed(() =>
  props.currentUser?.username ? props.currentUser.username.charAt(0).toUpperCase() : '?'
)

const announcements = ref([])
const annLoading = ref(false)
const annError = ref('')
const annMessage = ref('')
const newAnn = ref({ title: '', content: '' })

const loadAnnouncements = async () => {
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

watch(
  () => currentPage.value,
  (v) => {
    if (v === 'announcements') void loadAnnouncements()
  }
)

const students = ref([])
const teachers = ref([])
const usersLoaded = ref(false)
const usersLoading = ref(false)
const usersError = ref('')

const normalizeUserList = (payload) => {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.users)) return payload.users
  return []
}

const loadAllStudentsAndTeachers = async () => {
  if (usersLoading.value) return
  usersLoading.value = true
  usersError.value = ''
  try {
    const [stuResp, teaResp] = await Promise.all([listUsers('student'), listUsers('teacher')])
    students.value = normalizeUserList(stuResp?.data)
    teachers.value = normalizeUserList(teaResp?.data)
    usersLoaded.value = true
  } catch (e) {
    usersError.value = e?.response?.data?.message || e?.message || '加载学生/教师列表失败。'
    students.value = []
    teachers.value = []
    usersLoaded.value = false
  } finally {
    usersLoading.value = false
  }
}

watch(
  () => currentPage.value,
  (v) => {
    if (v === 'user-stats') {
      if (!usersLoaded.value) void loadAllStudentsAndTeachers()
    }
  }
)

onMounted(() => {
  void loadAnnouncements()
  void loadAllStudentsAndTeachers()
})

// =========================
// 课程权限（给每个老师分配可见课程）
// =========================
const allCourses = ref([])
const selectedTeacherId = ref(null)
const assignedCourseNames = ref([])
const coursePermCandidate = ref('')
const coursePermLoading = ref(false)
const coursePermError = ref('')
const coursePermMessage = ref('')

const courseManageName = ref('')
const courseManageLoading = ref(false)
const courseManageError = ref('')
const courseManageMessage = ref('')

// =========================
// 教师权限申请审批（带理由）
// =========================
const permReqLoading = ref(false)
const permReqError = ref('')
const pendingTeacherCoursePermissionRequests = ref([])

const permDecisionDialogVisible = ref(false)
const permDecisionSubmitting = ref(false)
const permDecisionError = ref('')
const permDecisionForm = ref({ requestId: null, decision: 'approve', reason: '', courseName: '', teacherUsername: '' })

const loadPendingTeacherCoursePermissionRequests = async () => {
  permReqLoading.value = true
  permReqError.value = ''
  try {
    const res = await listTeacherCoursePermissionRequests({ adminUserId: props.currentUser.id, status: 'PENDING' })
    const payload = res && res.data ? res.data : res
    pendingTeacherCoursePermissionRequests.value = Array.isArray(payload) ? payload : []
  } catch (e) {
    permReqError.value = e?.response?.data?.message || e?.message || '加载申请失败。'
    pendingTeacherCoursePermissionRequests.value = []
  } finally {
    permReqLoading.value = false
  }
}

const openPermDecisionDialog = (req, decision) => {
  permDecisionError.value = ''
  permDecisionForm.value = {
    requestId: req?.id ?? null,
    decision,
    reason: '',
    courseName: req?.courseName ?? '',
    teacherUsername: req?.teacherUsername ?? ''
  }
  permDecisionDialogVisible.value = true
}

const submitPermDecision = async () => {
  permDecisionError.value = ''
  if (!permDecisionForm.value.requestId) {
    permDecisionError.value = '申请记录不存在。'
    return
  }
  if (!permDecisionForm.value.reason || !permDecisionForm.value.reason.trim()) {
    permDecisionError.value = '请填写理由（通过或不通过都需要）。'
    return
  }

  permDecisionSubmitting.value = true
  try {
    await decideTeacherCoursePermissionRequest({
      adminUserId: props.currentUser.id,
      requestId: permDecisionForm.value.requestId,
      decision: permDecisionForm.value.decision,
      reason: permDecisionForm.value.reason.trim()
    })

    permDecisionDialogVisible.value = false
    await loadPendingTeacherCoursePermissionRequests()
    // 若正在查看同一位老师，也刷新其已授权课程列表
    if (selectedTeacherId.value) await loadAssignedCoursesForTeacher(selectedTeacherId.value)
  } catch (e) {
    permDecisionError.value = e?.response?.data?.message || e?.message || '操作失败。'
  } finally {
    permDecisionSubmitting.value = false
  }
}

const loadAllCoursesCatalog = async (force = false) => {
  if (!force && allCourses.value.length) return
  try {
    const res = await listCoursesByMajor()
    const payload = res && res.data ? res.data : res
    allCourses.value = Array.isArray(payload) ? payload : []
  } catch (e) {
    allCourses.value = []
  }
}

const loadAssignedCoursesForTeacher = async (teacherId) => {
  if (!teacherId) {
    assignedCourseNames.value = []
    return
  }
  coursePermError.value = ''
  try {
    const res = await listTeacherCoursePermissions(teacherId)
    const payload = res && res.data ? res.data : res
    assignedCourseNames.value = Array.isArray(payload?.courses) ? payload.courses : []
  } catch (e) {
    coursePermError.value = e?.response?.data?.message || e?.message || '加载课程权限失败。'
    assignedCourseNames.value = []
  }
}

const addTeacherCourse = () => {
  const name = String(coursePermCandidate.value || '').trim()
  if (!name) return
  if (!allCourses.value.includes(name)) return
  if (!assignedCourseNames.value.includes(name)) {
    assignedCourseNames.value = [...assignedCourseNames.value, name]
  }
}

const removeTeacherCourse = (name) => {
  assignedCourseNames.value = assignedCourseNames.value.filter((c) => c !== name)
}

const ensureCoursePermissionsPageReady = async () => {
  if (!usersLoaded.value) {
    await loadAllStudentsAndTeachers()
    // 避免在用户列表请求未完成时切到本页导致 teacher 列表为空
    let guard = 0
    while (!usersLoaded.value && guard < 20) {
      await new Promise((r) => setTimeout(r, 100))
      guard++
    }
  }
  await loadAllCoursesCatalog()
  if (!selectedTeacherId.value && Array.isArray(teachers.value) && teachers.value.length) {
    selectedTeacherId.value = teachers.value[0]?.id ?? null
  }
  await loadAssignedCoursesForTeacher(selectedTeacherId.value)
  await loadPendingTeacherCoursePermissionRequests()
}

watch(
  () => currentPage.value,
  (v) => {
    if (v === 'course-permissions') void ensureCoursePermissionsPageReady()
  },
  { immediate: true }
)

const saveTeacherCoursePermissions = async () => {
  coursePermLoading.value = true
  coursePermError.value = ''
  coursePermMessage.value = ''
  try {
    if (!selectedTeacherId.value) {
      coursePermError.value = '请选择老师。'
      return
    }
    await assignTeacherCourses({
      adminUserId: props.currentUser.id,
      teacherId: selectedTeacherId.value,
      courseNames: assignedCourseNames.value
    })
    coursePermMessage.value = '课程权限已更新。'
  } catch (e) {
    coursePermError.value = e?.response?.data?.message || e?.message || '保存失败。'
  } finally {
    coursePermLoading.value = false
  }
}

const refreshAllCoursesCatalog = async () => {
  await loadAllCoursesCatalog(true)
}

const handleAddCourse = async () => {
  courseManageLoading.value = true
  courseManageError.value = ''
  courseManageMessage.value = ''
  try {
    const name = (courseManageName.value || '').trim()
    if (!name) {
      courseManageError.value = '请输入课程名称。'
      return
    }
    await addCourse({ adminUserId: props.currentUser.id, courseName: name })
    courseManageName.value = ''
    courseManageMessage.value = '课程已添加。'

    await refreshAllCoursesCatalog()
    await loadAssignedCoursesForTeacher(selectedTeacherId.value)
  } catch (e) {
    courseManageError.value = e?.response?.data?.message || e?.message || '添加课程失败。'
  } finally {
    courseManageLoading.value = false
  }
}

const handleDeleteCourse = async (courseName) => {
  if (!courseName) return
  const ok = confirm(`确定删除课程「${courseName}」吗？该课程的知识点与权限会被一并清理。`)
  if (!ok) return
  courseManageLoading.value = true
  courseManageError.value = ''
  courseManageMessage.value = ''
  try {
    await deleteCourse({ adminUserId: props.currentUser.id, courseName })
    courseManageMessage.value = '课程已删除。'

    await refreshAllCoursesCatalog()
    await loadAssignedCoursesForTeacher(selectedTeacherId.value)
  } catch (e) {
    courseManageError.value = e?.response?.data?.message || e?.message || '删除课程失败。'
  } finally {
    courseManageLoading.value = false
  }
}

const submitAnnouncement = async () => {
  annMessage.value = ''
  annError.value = ''
  const title = (newAnn.value.title || '').trim()
  const content = (newAnn.value.content || '').trim()
  if (!title || !content) {
    annError.value = '请填写标题与正文。'
    return
  }
  annLoading.value = true
  try {
    await createAnnouncement({
      title,
      content,
      userId: props.currentUser.id
    })
    newAnn.value = { title: '', content: '' }
    annMessage.value = '公告已发布。'
    await loadAnnouncements()
  } catch (e) {
    annError.value = e?.response?.data?.message || '发布失败。'
  } finally {
    annLoading.value = false
  }
}

const handleDeleteAnn = async (id) => {
  if (!id || !confirm('确定删除该公告？')) return
  annError.value = ''
  try {
    await deleteAnnouncement(id, props.currentUser.id)
    annMessage.value = '已删除。'
    await loadAnnouncements()
  } catch (e) {
    annError.value = e?.response?.data?.message || '删除失败。'
  }
}

const importRole = ref('student')
const importFileInputRef = ref(null)
const selectedImportFile = ref(null)
const selectedImportFileName = ref('')
const importLoading = ref(false)
const importMessage = ref('')
const importError = ref('')
const importResult = ref(null)

const isHeaderRow = (r) => {
  if (!r || !r.length) return false
  const a = String(r[0] ?? '').trim()
  const b = String(r[1] ?? '').trim()
  const nameHead = a.includes('用户名') || a.includes('姓名') || a.includes('展示')
  const idHead = b.includes('学工') || b.includes('学号') || b.includes('工号')
  return nameHead && idHead
}

const cellToStr = (v) => {
  if (v == null || v === '') return ''
  if (typeof v === 'number' && Number.isFinite(v)) {
    return String(Math.trunc(v) === v ? v : v)
  }
  return String(v).trim()
}

const loadXlsx = async () => {
  const mod = await import('xlsx')
  return mod.default ?? mod
}

const parseXlsxToRows = async (file) => {
  const XLSX = await loadXlsx()
  const buf = await file.arrayBuffer()
  const wb = XLSX.read(buf, { type: 'array' })
  if (!wb.SheetNames?.length) return []
  const sheet = wb.Sheets[wb.SheetNames[0]]
  const matrix = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '', raw: true })
  let start = 0
  if (matrix.length && isHeaderRow(matrix[0])) start = 1
  const out = []
  for (let i = start; i < matrix.length; i++) {
    const row = matrix[i]
    if (!row) continue
    const username = cellToStr(row[0])
    const workId = cellToStr(row[1])
    if (!username && !workId) continue
    if (username && workId) out.push({ username, workId })
  }
  return out
}

const downloadImportTemplate = async () => {
  const XLSX = await loadXlsx()
  const ws = XLSX.utils.aoa_to_sheet([
    ['展示用户名', '学工号'],
    ['张三', '20210001'],
    ['李四', '20210002'],
    ['', ''],
    [
      '说明：勿删第1行表头；自第2行起填写。学工号列请在Excel中设为「文本」或加前导单引号，避免前导0丢失。初始密码=学工号。',
      ''
    ]
  ])
  ws['!cols'] = [{ wch: 18 }, { wch: 16 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '账号列表')
  XLSX.writeFile(wb, '师生账号批量导入模板.xlsx')
}

const onImportFileChange = (e) => {
  const f = e.target?.files?.[0] || null
  selectedImportFile.value = f
  selectedImportFileName.value = f?.name || ''
  importError.value = ''
  importMessage.value = ''
  importResult.value = null
}

const triggerImportFilePick = () => {
  importFileInputRef.value?.click()
}

const clearImportFile = () => {
  selectedImportFile.value = null
  selectedImportFileName.value = ''
  if (importFileInputRef.value) importFileInputRef.value.value = ''
  importResult.value = null
}

const submitBulkImport = async () => {
  importMessage.value = ''
  importError.value = ''
  importResult.value = null
  const file = selectedImportFile.value
  if (!file) {
    importError.value = '请先选择 .xlsx 文件，或使用上方按钮下载模板填写后再上传。'
    return
  }
  const lower = file.name.toLowerCase()
  if (!lower.endsWith('.xlsx')) {
    importError.value = '仅支持 .xlsx 格式（Excel 2007+）。'
    return
  }
  importLoading.value = true
  try {
    const rows = await parseXlsxToRows(file)
    if (!rows.length) {
      importError.value = '表格中未解析到有效数据：请确认第一张工作表第 A 列为展示用户名、第 B 列为学工号，且第1行为表头。'
      return
    }
    const { data } = await bulkImportUsers({
      userId: props.currentUser.id,
      role: importRole.value,
      rows
    })
    importResult.value = data
    importMessage.value = `已新建 ${data.created} 个账号。`
    if (data.failures?.length) importMessage.value += ` 未导入 ${data.failures.length} 条（见下方明细）。`
    clearImportFile()
  } catch (e) {
    importError.value = e?.response?.data?.message || e?.message || '导入失败，请检查文件是否为有效 xlsx。'
  } finally {
    importLoading.value = false
  }
}

const collegeDisplayName = (code) => {
  if (!code) return '未填写'
  const row = colleges.find((c) => c.code === code)
  return row ? row.name : String(code)
}

const formatAnnTime = (iso) => {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return String(iso)
  }
}

const openEditProfile = () => {
  editProfileForm.value.username = profileForm.value.username || props.currentUser.username || ''
  editProfileForm.value.email = profileForm.value.email || props.currentUser.email || ''
  editProfileForm.value.workId = profileForm.value.workId || props.currentUser.workId || ''
  editProfileVisible.value = true
}

const handleSaveProfile = async () => {
  try {
    const resp = await updateUser({
      userId: props.currentUser.id,
      username: editProfileForm.value.username,
      email: editProfileForm.value.email,
      workId: (editProfileForm.value.workId || '').trim() || undefined
    })
    editProfileVisible.value = false
    profileMessage.value = resp?.data?.message || '已更新'
    profileForm.value.username = editProfileForm.value.username
    profileForm.value.email = editProfileForm.value.email
    profileForm.value.workId = resp?.data?.user?.workId ?? editProfileForm.value.workId
    const updatedUser = resp?.data?.user || {
      ...props.currentUser,
      username: profileForm.value.username,
      email: profileForm.value.email,
      workId: profileForm.value.workId
    }
    try {
      localStorage.setItem('currentUser', JSON.stringify(updatedUser))
    } catch (e) {}
    emit('update-user', {
      username: updatedUser.username,
      email: updatedUser.email,
      workId: updatedUser.workId
    })
  } catch (err) {
    editProfileVisible.value = false
    profileMessage.value = err?.response?.data?.message || '同步失败。'
  }
}

const handlePasswordSave = async () => {
  if (!passwordPanelRef.value?.submitChange) return
  const ok = await passwordPanelRef.value.submitChange()
  if (ok) changePasswordVisible.value = false
}
</script>

<template>
    <section v-if="currentPage === 'profile'" class="panel-stack">
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
          <h3>概览</h3>
          <div class="profile-stat-list">
            <div>
              <span>已发布公告</span>
              <strong>{{ announcements.length }}</strong>
            </div>
            <div>
              <span>入口</span>
              <strong>公告管理</strong>
            </div>
          </div>
        </article>

        <article class="result-card profile-detail-card">
          <h3>账号信息</h3>
          <div class="grid-form">
            <label>
              用户名
              <div class="panel-subtitle">{{ profileForm.username || currentUser.username }}</div>
            </label>
            <label>
              学工号
              <div class="panel-subtitle">{{ profileForm.workId || currentUser.workId || '未设置' }}</div>
            </label>
            <label>
              邮箱
              <div class="panel-subtitle">{{ profileForm.email || currentUser.email }}</div>
            </label>
            <label>
              角色
              <div class="panel-subtitle">管理员</div>
            </label>
          </div>
          <div class="profile-btn-row">
            <button type="button" class="nav-btn" @click="openEditProfile">编辑资料</button>
            <button type="button" class="nav-btn" @click="changePasswordVisible = true">修改密码</button>
            <button class="danger-btn profile-logout-btn" @click="emit('logout')">退出登录</button>
          </div>
          <p v-if="profileMessage" class="ok-text">{{ profileMessage }}</p>
        </article>
      </div>
    </section>

    <section v-else-if="currentPage === 'user-stats'" class="panel-stack">
      <article class="result-card">
        <h3>用户统计与批量导入</h3>
        <div class="inline-form admin-import-actions" style="margin-top:12px;flex-wrap:wrap;gap:10px;align-items:center">
          <button type="button" class="match-button" @click="downloadImportTemplate">下载 Excel 模板</button>
          <input
            ref="importFileInputRef"
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            style="display:none"
            @change="onImportFileChange"
          />
          <button type="button" class="cancel-button" @click="triggerImportFilePick">选择 .xlsx 文件</button>
          <span v-if="selectedImportFileName" class="panel-subtitle" style="margin:0">已选：{{ selectedImportFileName }}</span>
          <button v-if="selectedImportFileName" type="button" class="cancel-button" @click="clearImportFile">清除</button>
        </div>
        <div class="grid-form single-col" style="margin-top:16px">
          <label>
            导入角色
            <select v-model="importRole" class="match-height">
              <option value="student">学生</option>
              <option value="teacher">教师</option>
            </select>
          </label>
        </div>
        <p v-if="importError" class="error-text">{{ importError }}</p>
        <p v-if="importMessage" class="ok-text">{{ importMessage }}</p>
        <div class="inline-form" style="margin-top:12px">
          <button type="button" class="match-button" :disabled="importLoading || !selectedImportFile" @click="submitBulkImport">
            {{ importLoading ? '导入中…' : '开始导入' }}
          </button>
        </div>
        <div v-if="importResult?.failures?.length" style="margin-top:16px">
          <h4>未导入明细</h4>
          <table class="data-table">
            <thead>
              <tr>
                <th>展示用户名</th>
                <th>学工号</th>
                <th>原因</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(f, idx) in importResult.failures" :key="idx">
                <td>{{ f.username }}</td>
                <td>{{ f.workId }}</td>
                <td>{{ f.reason }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="grid-form two-col" style="margin-top:24px">
          <article>
            <h3>学生统计</h3>
        <p v-if="usersLoading && !students.length" class="panel-subtitle">加载中…</p>
        <p v-else-if="usersError && !students.length" class="error-text">{{ usersError }}</p>

        <div class="profile-stat-list" style="margin-top:12px">
          <div>
            <span>学生总数</span>
            <strong>{{ students.length }}</strong>
          </div>
          <div>
            <span>数据状态</span>
            <strong>{{ usersLoaded ? '已加载' : (usersLoading ? '加载中' : '未加载') }}</strong>
          </div>
        </div>

            <div class="inline-form" style="margin-top:12px">
              <button type="button" class="match-button" :disabled="usersLoading" @click="loadAllStudentsAndTeachers">
                {{ usersLoading ? '刷新中…' : '刷新列表' }}
              </button>
            </div>
          </article>

          <article>
            <h3>教师统计</h3>
            <p v-if="usersLoading && !teachers.length" class="panel-subtitle">加载中…</p>
            <p v-else-if="usersError && !teachers.length" class="error-text">{{ usersError }}</p>

            <div class="profile-stat-list" style="margin-top:12px">
              <div>
                <span>教师总数</span>
                <strong>{{ teachers.length }}</strong>
              </div>
              <div>
                <span>数据状态</span>
                <strong>{{ usersLoaded ? '已加载' : (usersLoading ? '加载中' : '未加载') }}</strong>
              </div>
            </div>

            <div class="inline-form" style="margin-top:12px">
              <button type="button" class="match-button" :disabled="usersLoading" @click="loadAllStudentsAndTeachers">
                {{ usersLoading ? '刷新中…' : '刷新列表' }}
              </button>
            </div>
          </article>
        </div>
      </article>

      <article class="result-card">
        <h3>学生列表</h3>
        <div v-if="usersLoading && !students.length" class="panel-subtitle">加载中…</div>
        <div v-else-if="!students.length" class="panel-subtitle">暂无学生数据。</div>
        <div v-else style="max-height:520px;overflow:auto;">
          <table class="data-table">
            <thead>
              <tr>
                <th>用户名</th>
                <th>学工号</th>
                <th>邮箱</th>
                <th>专业（一级）</th>
                <th>专业（二级）</th>
                <th>专业（三级）</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in students" :key="s.id || s.workId || s.username">
                <td>{{ s.username || '-' }}</td>
                <td>{{ s.workId || '-' }}</td>
                <td>{{ s.email || '-' }}</td>
                <td>{{ s.major1 || '—' }}</td>
                <td>{{ s.major2 || '—' }}</td>
                <td>{{ s.major3 || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>

      <article class="result-card">
        <h3>教师列表</h3>
        <div v-if="usersLoading && !teachers.length" class="panel-subtitle">加载中…</div>
        <div v-else-if="!teachers.length" class="panel-subtitle">暂无教师数据。</div>
        <div v-else style="max-height:520px;overflow:auto;">
          <table class="data-table">
            <thead>
              <tr>
                <th>用户名</th>
                <th>学工号</th>
                <th>邮箱</th>
                <th>学院</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in teachers" :key="t.id || t.workId || t.username">
                <td>{{ t.username || '-' }}</td>
                <td>{{ t.workId || '-' }}</td>
                <td>{{ t.email || '-' }}</td>
                <td>{{ collegeDisplayName(t.college) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>
    </section>

    <section v-else-if="currentPage === 'announcements'" class="panel-stack">
      <article class="result-card">
        <h3>发布公告</h3>
        <p v-if="annError" class="error-text">{{ annError }}</p>
        <p v-if="annMessage" class="ok-text">{{ annMessage }}</p>
        <div class="grid-form single-col" style="margin-top:12px">
          <label>
            标题
            <input v-model="newAnn.title" class="match-height" placeholder="公告标题" />
          </label>
          <label>
            正文
            <textarea v-model="newAnn.content" rows="6" placeholder="公告内容"></textarea>
          </label>
        </div>
        <div class="inline-form" style="margin-top:12px">
          <button type="button" class="match-button" :disabled="annLoading" @click="submitAnnouncement">
            {{ annLoading ? '提交中…' : '发布' }}
          </button>
        </div>
      </article>

      <article class="result-card">
        <h3>公告列表</h3>
        <p v-if="annLoading && !announcements.length" class="panel-subtitle">加载中…</p>
        <p v-else-if="!announcements.length" class="panel-subtitle">暂无公告。</p>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>标题</th>
              <th>发布时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in announcements" :key="a.id">
              <td>{{ a.title }}</td>
              <td>{{ formatAnnTime(a.createdAt) }}</td>
              <td>
                <button type="button" class="cancel-button" @click="handleDeleteAnn(a.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </article>
    </section>

    <section v-else-if="currentPage === 'course-permissions'" class="panel-stack">
      <article class="result-card">
        <h3>课程权限分配</h3>
        <p class="panel-subtitle" style="margin-top:10px">
          选择一个教师后，为其逐个添加允许查看的课程。教师端只会展示这些被授权课程。
        </p>

        <div class="grid-form single-col" style="margin-top:12px">
          <label>
            选择教师
            <select v-model="selectedTeacherId" class="match-height" @change="loadAssignedCoursesForTeacher(selectedTeacherId)">
              <option v-for="t in teachers" :key="t.id || t.workId || t.username" :value="t.id">
                {{ t.username || '-' }}（{{ t.workId || '-' }}）
              </option>
            </select>
          </label>

          <label>
            允许查看的课程
            <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:6px">
              <select v-model="coursePermCandidate" class="match-height" style="min-width:10em">
                <option value="">选择课程</option>
                <option v-for="c in allCourses" :key="c" :value="c">{{ c }}</option>
              </select>
              <button type="button" class="match-button" :disabled="!coursePermCandidate" @click="addTeacherCourse">
                添加
              </button>
            </div>
            <div class="selected-chips" style="margin-top:8px;display:flex;flex-wrap:wrap;gap:8px">
              <span v-for="c in assignedCourseNames" :key="c" class="chip">
                {{ c }}
                <button type="button" class="chip-remove" @click="removeTeacherCourse(c)">×</button>
              </span>
            </div>
          </label>
        </div>

        <p v-if="coursePermError" class="error-text">{{ coursePermError }}</p>
        <p v-if="coursePermMessage" class="ok-text">{{ coursePermMessage }}</p>

        <div class="inline-form" style="margin-top:12px">
          <button type="button" class="match-button" :disabled="coursePermLoading" @click="saveTeacherCoursePermissions">
            {{ coursePermLoading ? '保存中…' : '保存权限' }}
          </button>
        </div>
      </article>

      <article class="result-card">
        <h3>当前授权概览</h3>
        <p v-if="coursePermLoading && selectedTeacherId" class="panel-subtitle">加载中…</p>
        <p v-else-if="selectedTeacherId && !assignedCourseNames.length" class="panel-subtitle">该教师当前没有可见课程。</p>
        <div v-else-if="selectedTeacherId && assignedCourseNames.length">
          <div class="profile-stat-list" style="margin-top:12px">
            <div>
              <span>授权课程数</span>
              <strong>{{ assignedCourseNames.length }}</strong>
            </div>
          </div>
          <ul class="panel-bullets" style="margin-top:10px">
            <li v-for="c in assignedCourseNames" :key="c">{{ c }}</li>
          </ul>
        </div>
        <p v-else class="panel-subtitle">请选择一名教师。</p>
      </article>

      <article class="result-card">
        <h3>课程管理</h3>
        <p class="panel-subtitle" style="margin-top:10px">
          添加/删除课程后，相关知识点与教师权限会被同步清理。
        </p>

        <div class="grid-form single-col" style="margin-top:12px">
          <label>
            新增课程名称
            <input v-model="courseManageName" class="match-height" placeholder="例如：高等数学" />
          </label>
        </div>

        <div class="inline-form" style="margin-top:12px">
          <button type="button" class="match-button" :disabled="courseManageLoading" @click="handleAddCourse">
            {{ courseManageLoading ? '处理中…' : '添加课程' }}
          </button>
        </div>

        <p v-if="courseManageError" class="error-text" style="margin-top:10px">{{ courseManageError }}</p>
        <p v-if="courseManageMessage" class="ok-text" style="margin-top:10px">{{ courseManageMessage }}</p>

        <div v-if="allCourses.length" style="margin-top:16px;max-height:320px;overflow:auto;">
          <table class="data-table">
            <thead>
              <tr>
                <th>课程名称</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in allCourses" :key="c">
                <td>{{ c }}</td>
                <td>
                  <button
                    type="button"
                    class="cancel-button"
                    :disabled="courseManageLoading"
                    @click="handleDeleteCourse(c)"
                  >
                    删除
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="panel-subtitle" style="margin-top:12px">暂无课程。</p>
      </article>

      <article class="result-card">
        <h3>教师权限申请（待审批）</h3>
        <p v-if="permReqLoading && !pendingTeacherCoursePermissionRequests.length" class="panel-subtitle" style="margin-top:10px">
          加载中…
        </p>
        <p v-else-if="permReqError" class="error-text" style="margin-top:10px">{{ permReqError }}</p>
        <p v-else-if="!pendingTeacherCoursePermissionRequests.length" class="panel-subtitle" style="margin-top:10px">
          暂无待审批申请。
        </p>
        <div v-else style="margin-top:16px;max-height:360px;overflow:auto;">
          <table class="data-table">
            <thead>
              <tr>
                <th>申请教师</th>
                <th>课程</th>
                <th>申请书</th>
                <th>审批</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in pendingTeacherCoursePermissionRequests" :key="r.id">
                <td>{{ r.teacherUsername || '-' }}</td>
                <td>{{ r.courseName }}</td>
                <td>
                  <div style="white-space:pre-wrap;max-width:520px;word-break:break-word;">{{ r.requestText }}</div>
                </td>
                <td>
                  <button
                    type="button"
                    class="match-button"
                    :disabled="permReqLoading"
                    @click="openPermDecisionDialog(r, 'approve')"
                  >
                    同意
                  </button>
                  <button
                    type="button"
                    class="cancel-button"
                    :disabled="permReqLoading"
                    style="margin-left:8px;"
                    @click="openPermDecisionDialog(r, 'reject')"
                  >
                    不同意
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>
    </section>

    <div v-if="permDecisionDialogVisible" class="modal-mask" @click.self="permDecisionDialogVisible = false">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" type="button" @click="permDecisionDialogVisible = false" aria-label="关闭">×</button>
          <h3>审批教师权限申请</h3>
          <p class="panel-subtitle" style="margin-top:8px">
            教师：<strong>{{ permDecisionForm.teacherUsername || '-' }}</strong> · 课程：<strong>{{ permDecisionForm.courseName }}</strong>
          </p>

          <div class="grid-form single-col" style="margin-top:12px">
            <label>
              理由（通过或不通过都需要）
              <textarea v-model="permDecisionForm.reason" rows="5" placeholder="请填写审批理由"></textarea>
            </label>
          </div>

          <div style="display:flex;gap:8px;margin-top:12px;">
            <button
              type="button"
              class="match-button"
              :disabled="permDecisionSubmitting"
              @click="submitPermDecision"
            >
              {{ permDecisionForm.decision === 'approve' ? (permDecisionSubmitting ? '处理中…' : '同意并授予') : (permDecisionSubmitting ? '处理中…' : '不同意并保留拒绝') }}
            </button>
            <button type="button" class="cancel-button" :disabled="permDecisionSubmitting" @click="permDecisionDialogVisible = false" style="margin-left:8px;">
              取消
            </button>
          </div>

          <p v-if="permDecisionError" class="error-text" style="margin-top:10px">{{ permDecisionError }}</p>
        </div>
      </div>
    </div>

    <div v-if="editProfileVisible" class="modal-mask" @click.self="editProfileVisible = false">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" type="button" @click="editProfileVisible = false" aria-label="关闭">×</button>
          <h3>编辑资料</h3>
          <div class="grid-form single-col" style="margin-top:12px">
            <label>
              用户名
              <input v-model="editProfileForm.username" class="match-height" />
            </label>
            <label>
              邮箱
              <input v-model="editProfileForm.email" class="match-height" type="email" />
            </label>
            <label>
              学工号
              <input v-model="editProfileForm.workId" class="match-height" placeholder="可选，用于学工号登录" />
            </label>
          </div>
          <div class="inline-form" style="margin-top:12px">
            <button type="button" class="match-button" @click="handleSaveProfile">保存</button>
            <button type="button" class="cancel-button" @click="editProfileVisible = false">取消</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="changePasswordVisible" class="modal-mask" @click.self="changePasswordVisible = false">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" type="button" @click="changePasswordVisible = false" aria-label="关闭">×</button>
          <h3>修改密码</h3>
          <div style="margin-top:12px">
            <AccountSecurityPanel ref="passwordPanelRef" :current-user="currentUser" :embedded="true" />
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <button type="button" class="match-button match-height" @click="handlePasswordSave">保存</button>
            <button type="button" class="cancel-button match-height" @click="changePasswordVisible = false">取消</button>
          </div>
        </div>
      </div>
    </div>
    <AiAssistantWidget role="admin" :current-user="currentUser" />
</template>

<style src="./student-portal.css"></style>
