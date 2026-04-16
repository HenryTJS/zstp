<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { listTeacherCoursePermissionRequests, decideTeacherCoursePermissionRequest } from '../../api/client'

const props = defineProps({
  adminUserId: { type: [Number, String], required: true }
})

const panelOpen = ref(false)
const pendingList = ref([])
const loading = ref(false)
// 轮询拉取失败时展示，避免静默清空列表
const listError = ref('')
let pollTimer = null

const decisionOpen = ref(false)
const decisionSubmitting = ref(false)
const decisionError = ref('')
const decisionForm = ref({
  requestId: null,
  decision: 'approve',
  reason: '',
  courseName: '',
  teacherUsername: '',
  requestKind: 'JOIN_EXISTING',
  requestText: ''
})

const pendingCount = () => pendingList.value.length

const fetchPending = async (silent = false) => {
  if (!props.adminUserId) return
  if (!silent) loading.value = true
  listError.value = ''
  try {
    const res = await listTeacherCoursePermissionRequests({
      adminUserId: props.adminUserId,
      status: 'PENDING'
    })
    const payload = res && res.data ? res.data : res
    pendingList.value = Array.isArray(payload) ? payload : []
  } catch (e) {
    listError.value = e?.response?.data?.message || e?.message || '加载待审批列表失败'
    if (!silent) pendingList.value = []
  } finally {
    if (!silent) loading.value = false
  }
}

watch(
  () => props.adminUserId,
  (id) => {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    if (id) {
      void fetchPending(false)
      pollTimer = window.setInterval(() => void fetchPending(true), 45000)
    } else {
      pendingList.value = []
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})

const togglePanel = () => {
  panelOpen.value = !panelOpen.value
  if (panelOpen.value) void fetchPending(false)
}

const openDecision = (req, decision) => {
  panelOpen.value = false
  decisionError.value = ''
  decisionForm.value = {
    requestId: req?.id ?? null,
    decision,
    reason: '',
    courseName: req?.courseName ?? '',
    teacherUsername: req?.teacherUsername ?? '',
    requestKind: req?.requestKind === 'CREATE_NEW' ? 'CREATE_NEW' : 'JOIN_EXISTING',
    requestText: req?.requestText ?? ''
  }
  decisionOpen.value = true
}

const closeDecision = () => {
  decisionOpen.value = false
}

const submitDecision = async () => {
  decisionError.value = ''
  if (!decisionForm.value.requestId) {
    decisionError.value = '申请记录不存在。'
    return
  }
  if (!decisionForm.value.reason || !decisionForm.value.reason.trim()) {
    decisionError.value = '请填写理由。'
    return
  }
  decisionSubmitting.value = true
  try {
    await decideTeacherCoursePermissionRequest({
      adminUserId: props.adminUserId,
      requestId: decisionForm.value.requestId,
      decision: decisionForm.value.decision,
      reason: decisionForm.value.reason.trim()
    })
    decisionOpen.value = false
    await fetchPending(false)
  } catch (e) {
    decisionError.value = e?.response?.data?.message || e?.message || '操作失败。'
  } finally {
    decisionSubmitting.value = false
  }
}

const excerpt = (t, n = 72) => {
  const s = String(t || '').trim()
  if (!s) return '（无正文）'
  return s.length <= n ? s : `${s.slice(0, n)}…`
}
</script>

<template>
  <div class="ap-bell-wrap">
    <button type="button" class="ap-bell-btn" aria-label="教师权限申请" @click="togglePanel">
      <svg class="ap-bell-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      <span v-if="pendingCount() > 0" class="ap-badge">{{ pendingCount() > 99 ? '99+' : pendingCount() }}</span>
    </button>

    <div v-if="panelOpen" class="ap-backdrop" @click="panelOpen = false" />
    <div v-if="panelOpen" class="ap-panel" role="dialog" aria-label="教师权限申请">
      <div class="ap-panel-head">
        <span>教师权限申请</span>
        <button type="button" class="ap-close" aria-label="关闭" @click="panelOpen = false">×</button>
      </div>
      <p v-if="loading" class="ap-muted">加载中…</p>
      <p v-else-if="listError" class="error-text" style="padding: 10px 12px; margin: 0">{{ listError }}</p>
      <ul v-else-if="!pendingList.length" class="ap-list ap-muted">暂无待审批申请</ul>
      <ul v-else class="ap-list">
        <li v-for="r in pendingList" :key="r.id" class="ap-item">
          <div class="ap-item-main">
            <span class="ap-item-title">{{ r.teacherUsername || '—' }} · {{ r.courseName }}</span>
            <span class="ap-item-tag">{{ r.requestKind === 'CREATE_NEW' ? '新增课程' : '加入已有' }}</span>
            <span class="ap-item-body">{{ excerpt(r.requestText) }}</span>
          </div>
          <div class="ap-item-actions">
            <button type="button" class="match-button ap-btn-ok" @click="openDecision(r, 'approve')">同意</button>
            <button type="button" class="cancel-button ap-btn-no" @click="openDecision(r, 'reject')">不同意</button>
          </div>
        </li>
      </ul>
    </div>

    <div v-if="decisionOpen" class="modal-mask" @click.self="closeDecision">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" type="button" @click="closeDecision" aria-label="关闭">×</button>
          <h3 class="portal-section-title portal-section-title--rose">审批教师权限申请</h3>
          <p class="panel-subtitle" style="margin-top: 8px">
            教师：<strong>{{ decisionForm.teacherUsername || '-' }}</strong>
            · 类型：
            <strong>{{ decisionForm.requestKind === 'CREATE_NEW' ? '新增课程（同意后将写入目录并授权）' : '加入已有课程' }}</strong>
            · 课程：<strong>{{ decisionForm.courseName }}</strong>
          </p>
          <div
            v-if="decisionForm.requestText"
            class="panel-subtitle"
            style="margin-top: 10px; white-space: pre-wrap; max-height: 120px; overflow: auto"
          >
            {{ decisionForm.requestText }}
          </div>

          <div class="grid-form single-col" style="margin-top: 12px">
            <label>
              理由（通过或不通过均需填写）
              <textarea v-model="decisionForm.reason" rows="5" placeholder="请填写审批理由"></textarea>
            </label>
          </div>

          <div class="ui-actions-row">
            <button type="button" class="match-button" :disabled="decisionSubmitting" @click="submitDecision">
              {{
                decisionForm.decision === 'approve'
                  ? decisionSubmitting ? '处理中…' : '同意并授权'
                  : decisionSubmitting ? '处理中…' : '不同意'
              }}
            </button>
            <button type="button" class="cancel-button" :disabled="decisionSubmitting" @click="closeDecision">
              取消
            </button>
          </div>
          <p v-if="decisionError" class="error-text" style="margin-top: 10px">{{ decisionError }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ap-bell-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
}
.ap-bell-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.06);
  color: #334155;
  cursor: pointer;
  transition: background 0.15s ease;
}
.ap-bell-btn:hover {
  background: rgba(15, 23, 42, 0.1);
}
.ap-bell-icon {
  width: 22px;
  height: 22px;
}
.ap-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #dc2626;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}
.ap-backdrop {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: transparent;
}
.ap-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 210;
  width: min(400px, 92vw);
  max-height: min(480px, 72vh);
  overflow: auto;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.15);
}
.ap-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  font-weight: 600;
}
.ap-close {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  color: #64748b;
}
.ap-muted {
  padding: 12px;
  color: #64748b;
  font-size: 0.9rem;
}
.ap-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.ap-item {
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  padding: 10px 12px;
}
.ap-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ap-item-title {
  font-weight: 600;
  color: #0f172a;
  font-size: 0.92rem;
}
.ap-item-tag {
  font-size: 0.75rem;
  color: #64748b;
}
.ap-item-body {
  font-size: 0.82rem;
  color: #475569;
  line-height: 1.35;
}
.ap-item-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.ap-btn-ok {
  min-height: 30px;
  padding: 5px 12px;
  font-size: 0.85rem;
}
.ap-btn-ok:hover {
  filter: brightness(.98);
}
.ap-btn-no {
  min-height: 30px;
  padding: 5px 12px;
  font-size: 0.85rem;
}
.ap-btn-no:hover {
  filter: brightness(.98);
}
</style>
<style>
@import '@/student/styles/student-portal.css';
</style>

