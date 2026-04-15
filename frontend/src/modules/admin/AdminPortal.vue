<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  bulkImportUsers,
  createAnnouncement,
  deleteAnnouncement,
  fetchAnnouncements,
  http,
  listCourseConfigs,
  listUsers,
  listCoursesByMajor,
  updateCourseConfig,
  updateUser
} from '../../api/client'
import { appShellKey } from '../../appShell'
import AccountSecurityPanel from '../../components/AccountSecurityPanel.vue'
import AiAssistantWidget from '../../components/AiAssistantWidget.vue'
import colleges from '../../data/colleges.json'

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
const shell = inject(appShellKey, null)
const relayLogout = () => {
  if (typeof shell?.logout === 'function') void shell.logout()
  else emit('logout')
}
const relayUpdateUser = (patch) => {
  if (typeof shell?.updateUser === 'function') shell.updateUser(patch)
  else emit('update-user', patch)
}
const route = useRoute()

const adminPathSegment = computed(() => {
  const p = route.path
  if (!p.startsWith('/admin')) return 'profile'
  let rest = p.slice('/admin'.length)
  if (rest.startsWith('/')) rest = rest.slice(1)
  const seg = (rest.split('/')[0] || '').trim()
  return seg || 'profile'
})

const currentPage = ref(adminPathSegment.value || props.activePage || 'profile')
watch(
  () => adminPathSegment.value,
  (v) => {
    currentPage.value = v || props.activePage || 'profile'
  },
  { immediate: true }
)

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
  if (!id || !confirm('确定删除该公告吗？')) return
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
  const nameHead = a.includes('用户名') || a.includes('姓名') || a.includes('显示')
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
    ['显示用户名', '学工号'],
    ['寮犱笁', '20210001'],
    ['鏉庡洓', '20210002'],
    ['', ''],
    [
      '说明：勿删除第1行表头；自第2行起填写。学工号列请在 Excel 中设为“文本”或加前导单引号，避免前导 0 丢失。初始密码=学工号。',
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
      importError.value = '表格中未解析到有效数据：请确认第一张工作表第 A 列为显示用户名、第 B 列为学工号，且第1行为表头。'
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
    relayUpdateUser({
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

// =========================
// 课程权重与学分配置
// =========================

const courseConfigLoading = ref(false)
const courseConfigError = ref('')
const courseConfigMessage = ref('')

const allCourses = ref([])
const selectedCourse = ref('')
const courseConfigsByName = ref({}) // { [courseName]: { courseName, weights, creditRules } }

const weightForm = ref({
  logicReasoning: 0.2,
  numericCalculation: 0.2,
  semanticUnderstanding: 0.2,
  spatialImagination: 0.2,
  memoryRetrieval: 0.2
})

/** 一条规则：同一学分 + 多选专业 code；majorCodes 为最终集合，selL1/2/3 仅用于多选框暂存 */
const creditRulesForm = ref([])

const majorFlat = ref([]) // { code, name, level, parentCode }

function flattenMajorNodes(nodes, parentCode = null, level = 1, out = []) {
  if (!Array.isArray(nodes)) return out
  for (const n of nodes) {
    const code = String(n.code ?? '').trim()
    if (code) out.push({ code, name: n.name || '', level, parentCode })
    if (Array.isArray(n.subfields) && n.subfields.length) {
      flattenMajorNodes(n.subfields, code, level + 1, out)
    }
  }
  return out
}

const loadMajorFlat = async () => {
  if (majorFlat.value.length) return
  try {
    const { data } = await http.get('/majors/tree?level=1')
    majorFlat.value = flattenMajorNodes(data || [])
  } catch {
    majorFlat.value = []
  }
}

const majorsL1 = computed(() => majorFlat.value.filter((m) => m.level === 1))

const l2OptionsForRow = (r) => {
  const sel = r.selL1 || []
  const all = majorFlat.value.filter((m) => m.level === 2)
  if (!sel.length) return all
  const set = new Set(sel.map(String))
  return all.filter((m) => set.has(String(m.parentCode)))
}

const l3OptionsForRow = (r) => {
  const sel = r.selL2 || []
  const all = majorFlat.value.filter((m) => m.level === 3)
  if (!sel.length) return all
  const set = new Set(sel.map(String))
  return all.filter((m) => set.has(String(m.parentCode)))
}

const mergeSelectionsIntoGroup = (r) => {
  const add = [...(r.selL1 || []), ...(r.selL2 || []), ...(r.selL3 || [])].map(String)
  const set = new Set([...(r.majorCodes || []).map(String), ...add])
  r.majorCodes = [...set]
  r.selL1 = []
  r.selL2 = []
  r.selL3 = []
}

const removeMajorCode = (r, code) => {
  r.majorCodes = (r.majorCodes || []).filter((c) => String(c) !== String(code))
}

const stepRound = (v, step) => Math.round((Number(v) || 0) / step) * step
const clamp = (v, min, max) => Math.max(min, Math.min(max, v))
const fmt2 = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}

const setWeightFromInput = (key, raw) => {
  const n = Number(raw)
  weightForm.value[key] = Number.isFinite(n) ? n : 0
}

const normalizeSingleWeight = (key) => {
  const min = 0.05
  const step = 0.05
  const raw = Number(weightForm.value[key] || 0)
  weightForm.value[key] = clamp(stepRound(raw, step), min, 1)
}

const isStepMultiple = (v, step) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return false
  const q = n / step
  return Math.abs(q - Math.round(q)) < 1e-9
}

const validateWeightsForSave = () => {
  const keys = ['logicReasoning', 'numericCalculation', 'semanticUnderstanding', 'spatialImagination', 'memoryRetrieval']
  for (const k of keys) {
    const v = Number(weightForm.value[k] || 0)
    if (!Number.isFinite(v)) return { ok: false, message: '权重必须为数字。' }
    if (v < 0.05 - 1e-12) return { ok: false, message: '每项权重不得低于 0.05。' }
    if (!isStepMultiple(v, 0.05)) return { ok: false, message: '权重必须以 0.05 为单位调整。' }
  }
  const s = keys.reduce((sum, k) => sum + Number(weightForm.value[k] || 0), 0)
  if (Math.abs(s - 1) > 1e-9) return { ok: false, message: '五维权重之和必须等于 1。' }
  return { ok: true, message: '' }
}

const weightSumOk = computed(() => {
  const s =
    Number(weightForm.value.logicReasoning || 0) +
    Number(weightForm.value.numericCalculation || 0) +
    Number(weightForm.value.semanticUnderstanding || 0) +
    Number(weightForm.value.spatialImagination || 0) +
    Number(weightForm.value.memoryRetrieval || 0)
  return Math.abs(s - 1) < 1e-9
})

const loadCourseConfigs = async () => {
  courseConfigLoading.value = true
  courseConfigError.value = ''
  courseConfigMessage.value = ''
  try {
    const [courseResp, cfgResp] = await Promise.all([
      listCoursesByMajor(undefined),
      listCourseConfigs(props.currentUser.id)
    ])
    allCourses.value = Array.isArray(courseResp?.data) ? courseResp.data : []
    const items = Array.isArray(cfgResp?.data?.items) ? cfgResp.data.items : []
    const map = {}
    for (const it of items) {
      if (it?.courseName) map[it.courseName] = it
    }
    courseConfigsByName.value = map
    if (!selectedCourse.value) selectedCourse.value = allCourses.value[0] || ''
    applySelectedCourseConfig()
  } catch (e) {
    courseConfigError.value = e?.response?.data?.message || e?.message || '加载课程配置失败。'
  } finally {
    courseConfigLoading.value = false
  }
}

const applySelectedCourseConfig = () => {
  const c = selectedCourse.value
  const cfg = c ? courseConfigsByName.value?.[c] : null
  const w = cfg?.weights || {}
  weightForm.value.logicReasoning = Number(w.logicReasoning ?? 0.2)
  weightForm.value.numericCalculation = Number(w.numericCalculation ?? 0.2)
  weightForm.value.semanticUnderstanding = Number(w.semanticUnderstanding ?? 0.2)
  weightForm.value.spatialImagination = Number(w.spatialImagination ?? 0.2)
  weightForm.value.memoryRetrieval = Number(w.memoryRetrieval ?? 0.2)
  creditRulesForm.value = Array.isArray(cfg?.creditRules)
    ? cfg.creditRules.map((r) => ({
        credit: Number(r.credit ?? 0.5),
        majorCodes: Array.isArray(r.majorCodes) ? [...r.majorCodes] : [],
        selL1: [],
        selL2: [],
        selL3: []
      }))
    : []
}

watch(() => selectedCourse.value, () => applySelectedCourseConfig())

watch(
  () => currentPage.value,
  (v) => {
    if (v === 'course-configs') {
      void loadCourseConfigs()
      void loadMajorFlat()
    }
  },
  { immediate: true }
)

const addCreditRule = () => {
  creditRulesForm.value = [
    ...(creditRulesForm.value || []),
    { credit: 0.5, majorCodes: [], selL1: [], selL2: [], selL3: [] }
  ]
}

const removeCreditRule = (idx) => {
  creditRulesForm.value = (creditRulesForm.value || []).filter((_, i) => i !== idx)
}

const normalizeCreditRules = () => {
  const step = 0.5
  const min = 0.5
  const max = 10
  creditRulesForm.value = (creditRulesForm.value || []).map((r) => {
    const credit = clamp(stepRound(Number(r.credit || 0), step), min, max)
    const codes = [...new Set((r.majorCodes || []).map((c) => String(c || '').trim()).filter(Boolean))]
    return {
      ...r,
      credit,
      majorCodes: codes
    }
  })
}

const saveSelectedCourseConfig = async () => {
  courseConfigMessage.value = ''
  courseConfigError.value = ''
  const c = selectedCourse.value
  if (!c) {
    courseConfigError.value = '请先选择课程。'
    return
  }
  normalizeCreditRules()
  const v = validateWeightsForSave()
  if (!v.ok) {
    courseConfigError.value = v.message || '保存失败。'
    return
  }
  courseConfigLoading.value = true
  try {
    const payload = {
      weights: { ...weightForm.value },
      creditRules: (creditRulesForm.value || [])
        .map((r) => ({
          credit: Number(r.credit || 0.5),
          majorCodes: [...new Set((r.majorCodes || []).map((c) => String(c || '').trim()).filter(Boolean))]
        }))
        .filter((r) => r.majorCodes.length > 0)
    }
    const resp = await updateCourseConfig(props.currentUser.id, c, payload)
    courseConfigsByName.value = { ...(courseConfigsByName.value || {}), [c]: resp?.data || payload }
    courseConfigMessage.value = '已保存。'
  } catch (e) {
    courseConfigError.value = e?.response?.data?.message || e?.message || '保存失败。'
  } finally {
    courseConfigLoading.value = false
  }
}
</script>

<template>
    <section v-if="currentPage === 'profile'" class="panel-stack admin-theme">
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
          <h3 class="portal-section-title">概览</h3>
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
          <h3 class="portal-section-title portal-section-title--violet">账号信息</h3>
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
            <button type="button" class="danger-btn profile-logout-btn" @click="relayLogout">退出登录</button>
          </div>
          <p v-if="profileMessage" class="ok-text">{{ profileMessage }}</p>
        </article>
      </div>
    </section>

    <section v-else-if="currentPage === 'user-stats'" class="panel-stack admin-theme">
      <article class="result-card">
        <h3 class="portal-section-title portal-section-title--teal">用户统计与批量导入</h3>
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
                <th>显示用户名</th>
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
            <h3 class="portal-section-title portal-section-title--emerald">学生统计</h3>
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
            <h3 class="portal-section-title portal-section-title--amber">教师统计</h3>
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
        <h3 class="portal-section-title portal-section-title--rose">学生列表</h3>
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
        <h3 class="portal-section-title portal-section-title--cyan">教师列表</h3>
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

    <section v-else-if="currentPage === 'course-configs'" class="panel-stack admin-theme">
      <article class="result-card">
        <h3 class="portal-section-title portal-section-title--orange">课程权重与学分配置</h3>

        <p v-if="courseConfigError" class="error-text">{{ courseConfigError }}</p>
        <p v-if="courseConfigMessage" class="ok-text">{{ courseConfigMessage }}</p>

        <div class="ui-actions-row course-config-course-row">
          <label style="min-width: 220px">
            <span class="panel-subtitle">选择课程</span>
            <select v-model="selectedCourse" class="match-height" :disabled="courseConfigLoading">
              <option v-for="c in allCourses" :key="c" :value="c">{{ c }}</option>
            </select>
          </label>
          <button type="button" class="cancel-button" :disabled="courseConfigLoading" @click="loadCourseConfigs">
            {{ courseConfigLoading ? '加载中…' : '刷新' }}
          </button>
        </div>

        <div class="grid-form two-col ui-mt-12">
          <label>
            逻辑推理
            <input
              :value="fmt2(weightForm.logicReasoning)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              @input="setWeightFromInput('logicReasoning', $event.target.value)"
              @change="normalizeSingleWeight('logicReasoning')"
            />
          </label>
          <label>
            数量计算
            <input
              :value="fmt2(weightForm.numericCalculation)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              @input="setWeightFromInput('numericCalculation', $event.target.value)"
              @change="normalizeSingleWeight('numericCalculation')"
            />
          </label>
          <label>
            语义理解
            <input
              :value="fmt2(weightForm.semanticUnderstanding)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              @input="setWeightFromInput('semanticUnderstanding', $event.target.value)"
              @change="normalizeSingleWeight('semanticUnderstanding')"
            />
          </label>
          <label>
            空间想象
            <input
              :value="fmt2(weightForm.spatialImagination)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              @input="setWeightFromInput('spatialImagination', $event.target.value)"
              @change="normalizeSingleWeight('spatialImagination')"
            />
          </label>
          <label>
            记忆检索
            <input
              :value="fmt2(weightForm.memoryRetrieval)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              @input="setWeightFromInput('memoryRetrieval', $event.target.value)"
              @change="normalizeSingleWeight('memoryRetrieval')"
            />
          </label>
          <div>
            <div class="panel-subtitle">权重校验</div>
            <p class="panel-subtitle" style="margin: 6px 0 0">
              合计：<strong>{{ (
                weightForm.logicReasoning +
                weightForm.numericCalculation +
                weightForm.semanticUnderstanding +
                weightForm.spatialImagination +
                weightForm.memoryRetrieval
              ).toFixed(2) }}</strong>
              <span v-if="!weightSumOk" class="error-text" style="margin-left: 8px">必须等于 1</span>
            </p>
          </div>
        </div>

        <article class="result-card ui-mt-12">
          <h3 class="portal-section-title portal-section-title--sky">学分规则</h3>

          <div class="ui-actions-row">
            <button type="button" class="cancel-button" :disabled="courseConfigLoading" @click="addCreditRule">添加学分组</button>
          </div>

          <div v-for="(r, idx) in creditRulesForm" :key="idx" class="credit-rule-block ui-mt-12">
            <div class="ui-actions-row" style="align-items: flex-end; flex-wrap: wrap; gap: 12px">
              <label style="min-width: 140px">
                学分
                <input
                  v-model.number="r.credit"
                  type="number"
                  min="0.5"
                  max="10"
                  step="0.5"
                  class="match-height"
                />
              </label>
              <button type="button" class="cancel-button" @click="removeCreditRule(idx)">删除本组</button>
            </div>
            <div class="credit-major-chips ui-mt-8">
              <span v-for="c in r.majorCodes" :key="c" class="credit-major-chip">
                {{ c }}
                <button type="button" class="credit-chip-remove" :aria-label="'移除 ' + c" @click="removeMajorCode(r, c)">×</button>
              </span>
              <span v-if="!r.majorCodes?.length" class="panel-subtitle">本组尚未加入任何专业 code（保存时本组会被忽略）</span>
            </div>
            <div class="grid-form three-col ui-mt-8">
              <label>
                一级（多选）
                <select v-model="r.selL1" multiple class="major-multi" size="6">
                  <option v-for="m in majorsL1" :key="m.code" :value="m.code">{{ m.name }}（{{ m.code }}）</option>
                </select>
              </label>
              <label>
                二级（多选）
                <select v-model="r.selL2" multiple class="major-multi" size="6">
                  <option v-for="m in l2OptionsForRow(r)" :key="m.code" :value="m.code">{{ m.name }}（{{ m.code }}）</option>
                </select>
              </label>
              <label>
                三级（多选）
                <select v-model="r.selL3" multiple class="major-multi" size="6">
                  <option v-for="m in l3OptionsForRow(r)" :key="m.code" :value="m.code">{{ m.name }}（{{ m.code }}）</option>
                </select>
              </label>
            </div>
            <div class="ui-mt-8">
              <button type="button" class="cancel-button" @click="mergeSelectionsIntoGroup(r)">将所选加入本组</button>
            </div>
          </div>
        </article>

        <div class="ui-actions-row">
          <button type="button" class="match-button" :disabled="courseConfigLoading || !weightSumOk" @click="saveSelectedCourseConfig">
            {{ courseConfigLoading ? '保存中…' : '保存配置' }}
          </button>
        </div>
      </article>
    </section>

    <section v-else-if="currentPage === 'announcements'" class="panel-stack admin-theme">
      <article class="result-card">
        <h3 class="portal-section-title portal-section-title--violet">发布公告</h3>
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
        <h3 class="portal-section-title portal-section-title--emerald">公告列表</h3>
        <p v-if="annLoading && !announcements.length" class="panel-subtitle">加载中…</p>
        <table v-else-if="announcements.length" class="data-table">
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

    <div v-if="editProfileVisible" class="modal-mask" @click.self="editProfileVisible = false">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" type="button" @click="editProfileVisible = false" aria-label="关闭">×</button>
          <h3 class="portal-section-title portal-section-title--teal">编辑资料</h3>
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
          <h3 class="portal-section-title portal-section-title--slate">修改密码</h3>
          <div class="ui-mt-12">
            <AccountSecurityPanel ref="passwordPanelRef" :current-user="currentUser" :embedded="true" />
          </div>
          <div class="ui-actions-row">
            <button type="button" class="match-button match-height" @click="handlePasswordSave">保存</button>
            <button type="button" class="cancel-button match-height" @click="changePasswordVisible = false">取消</button>
          </div>
        </div>
      </div>
    </div>
    <AiAssistantWidget role="admin" :current-user="currentUser" />
</template>

<style>
@import '@/styles/student/student-portal.css';
</style>
<style scoped src="@/styles/admin/admin-portal-scoped.css"></style>


