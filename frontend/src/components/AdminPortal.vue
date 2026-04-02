<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { bulkImportUsers, createAnnouncement, deleteAnnouncement, fetchAnnouncements, updateUser } from '../api/client'
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

onMounted(() => {
  void loadAnnouncements()
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
  <section class="panel">
    <div class="panel-header">
      <h2>管理员端 · 系统管理</h2>
    </div>

    <section v-if="currentPage === 'profile'" class="panel-stack">
      <article class="result-card profile-hero-card">
        <div class="profile-hero-main">
          <div class="profile-avatar">{{ userInitial }}</div>
          <div>
            <h3>{{ profileForm.username || currentUser.username }}</h3>
          </div>
        </div>
        <div class="profile-hero-actions">
          <p class="panel-subtitle" style="margin:0">管理员账号，可发布公告供师生查看。</p>
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
          <p class="panel-subtitle" style="margin-top:10px">在顶部导航进入「批量导入」或「公告管理」。</p>
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

    <section v-else-if="currentPage === 'import'" class="panel-stack">
      <article class="result-card">
        <h3>批量导入学生 / 教师（Excel）</h3>
        <p class="panel-subtitle">
          请下载模板，按<strong>第一张工作表</strong>填写：<strong>A 列</strong>展示用户名，<strong>B 列</strong>学工号；第 1 行为表头勿删。仅支持 <code>.xlsx</code>。初始密码与学工号相同；登录可用学工号或系统占位邮箱（如
          <code>s学工号@bulk.import.local</code>）。
        </p>
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
            <textarea v-model="newAnn.content" rows="6" placeholder="公告内容（学生端与教师端可见）"></textarea>
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
  </section>
</template>

<style scoped src="./student-portal.css"></style>
