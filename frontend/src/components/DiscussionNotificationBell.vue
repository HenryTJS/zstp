<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  deleteUserNotification,
  listUserNotifications,
  markAllUserNotificationsRead,
  markUserNotificationRead
} from '../api/client'

const props = defineProps({
  userId: { type: [Number, String], required: true }
})

const route = useRoute()
const router = useRouter()

const open = ref(false)
const items = ref([])
const unreadCount = ref(0)
const loading = ref(false)
const busy = ref(false)
const annOpen = ref(false)
const annItem = ref(null)
let pollTimer = null

const roleFromPath = computed(() => {
  if (route.path.startsWith('/student')) return 'student'
  if (route.path.startsWith('/teacher')) return 'teacher'
  return null
})

const fetchList = async () => {
  if (!props.userId) return
  loading.value = true
  try {
    const { data } = await listUserNotifications(props.userId, { limit: 40 })
    items.value = Array.isArray(data?.items) ? data.items : []
    unreadCount.value = typeof data?.unreadCount === 'number' ? data.unreadCount : 0
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

const startPoll = () => {
  if (pollTimer) return
  void fetchList()
  pollTimer = window.setInterval(() => void fetchList(), 45000)
}

const stopPoll = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

watch(
  () => props.userId,
  (id) => {
    stopPoll()
    if (id) startPoll()
    else {
      items.value = []
      unreadCount.value = 0
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => stopPoll)

const navigateFor = async (n) => {
  const cn = n?.courseName || ''
  const pn = n?.pointName || ''
  const postId = n?.postId
  if (!cn || !pn) return
  const q = { dc: cn, dp: pn, ...(postId != null ? { dpost: String(postId) } : {}) }
  const r = roleFromPath.value
  if (r === 'student') {
    await router.push({ path: '/student/graph', query: q })
  } else if (r === 'teacher') {
    await router.push({ path: '/teacher/manage', query: q })
  }
}

const onItemClick = async (n) => {
  if (!n?.id || !props.userId) return
  // Announcement: open modal instead of deep-link navigation.
  if (String(n.type || '').toUpperCase() === 'ANNOUNCEMENT') {
    annItem.value = n
    annOpen.value = true
    try {
      await markUserNotificationRead(n.id, props.userId)
    } catch {
      /* ignore */
    }
    void fetchList()
    return
  }

  // 管理员审批「课程权限 / 新开课程」申请（与后端 TEACHER_PERMISSION 一致）
  if (String(n.type || '').toUpperCase() === 'TEACHER_PERMISSION') {
    open.value = false
    try {
      await markUserNotificationRead(n.id, props.userId)
    } catch {
      /* ignore */
    }
    void fetchList()
    const cn = String(n.courseName || '').trim()
    if (cn && roleFromPath.value === 'teacher') {
      await router.push({ path: '/teacher/course-detail', query: { course: cn } })
    }
    return
  }

  open.value = false
  try {
    await markUserNotificationRead(n.id, props.userId)
  } catch {
    /* still navigate */
  }
  void fetchList()
  await navigateFor(n)
}

const toggleOpen = () => {
  open.value = !open.value
  if (open.value) void fetchList()
}

const onMarkAllRead = async () => {
  if (!props.userId || busy.value) return
  busy.value = true
  try {
    await markAllUserNotificationsRead(props.userId)
  } finally {
    busy.value = false
  }
  void fetchList()
}

const onDeleteItem = async (n) => {
  if (!n?.id || !props.userId || busy.value) return
  busy.value = true
  try {
    await deleteUserNotification(n.id, props.userId)
  } finally {
    busy.value = false
  }
  void fetchList()
}

const formatTime = (iso) => {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return String(iso)
  }
}
</script>

<template>
  <div class="dn-bell-wrap">
    <button type="button" class="dn-bell-btn" aria-label="消息通知" @click="toggleOpen">
      <svg class="dn-bell-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      <span v-if="unreadCount > 0" class="dn-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </button>

    <div v-if="open" class="dn-backdrop" @click="open = false" />
    <div v-if="open" class="dn-panel" role="dialog" aria-label="通知列表">
      <div class="dn-panel-head">
        <span>通知</span>
        <div class="dn-head-actions">
          <button
            type="button"
            class="dn-link"
            :disabled="busy || unreadCount <= 0"
            @click="onMarkAllRead"
          >
            一键已读
          </button>
          <button type="button" class="dn-close" aria-label="关闭" @click="open = false">×</button>
        </div>
      </div>
      <p v-if="loading" class="dn-muted">加载中…</p>
      <ul v-else-if="!items.length" class="dn-list dn-muted">暂无通知</ul>
      <ul v-else class="dn-list">
        <li v-for="n in items" :key="n.id" class="dn-item" :class="{ unread: !n.read }">
          <div class="dn-item-row">
            <button type="button" class="dn-item-btn" @click="onItemClick(n)">
              <span class="dn-item-title">
                <span v-if="String(n.type || '').toUpperCase() === 'ANNOUNCEMENT'" class="dn-tag">公告</span>
                <span v-else-if="String(n.type || '').toUpperCase() === 'TEACHER_PERMISSION'" class="dn-tag">课程</span>
                {{ n.title }}
              </span>
              <span v-if="n.body" class="dn-item-body">{{ n.body }}</span>
              <span class="dn-item-meta">{{ formatTime(n.createdAt) }}</span>
            </button>
            <button type="button" class="dn-del" aria-label="删除通知" :disabled="busy" @click="onDeleteItem(n)">
              ×
            </button>
          </div>
        </li>
      </ul>
    </div>

    <div v-if="annOpen" class="dn-backdrop" @click="annOpen = false" />
    <div v-if="annOpen" class="dn-modal" role="dialog" aria-label="公告详情">
      <div class="dn-modal-head">
        <span>公告</span>
        <button type="button" class="dn-close" aria-label="关闭" @click="annOpen = false">×</button>
      </div>
      <div class="dn-modal-body">
        <h3 class="dn-modal-title">{{ annItem?.title }}</h3>
        <p class="dn-modal-meta">{{ formatTime(annItem?.createdAt) }}</p>
        <div class="dn-modal-content">{{ annItem?.body }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dn-bell-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
}
.dn-bell-btn {
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
.dn-bell-btn:hover {
  background: rgba(15, 23, 42, 0.1);
}
.dn-bell-icon {
  width: 22px;
  height: 22px;
}
.dn-badge {
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
.dn-backdrop {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: transparent;
}
.dn-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 210;
  width: min(360px, 92vw);
  max-height: min(420px, 70vh);
  overflow: auto;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.15);
}
.dn-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  font-weight: 600;
}
.dn-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.dn-link {
  border: none;
  background: transparent;
  color: #2563eb;
  font: inherit;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 8px;
}
.dn-link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.dn-close {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  color: #64748b;
}
.dn-muted {
  padding: 12px;
  color: #64748b;
  font-size: 0.9rem;
}
.dn-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.dn-item {
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}
.dn-item.unread {
  background: rgba(37, 99, 235, 0.06);
}
.dn-item-row {
  display: flex;
  align-items: stretch;
}
.dn-item-btn {
  display: block;
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
}
.dn-del {
  flex: 0 0 auto;
  width: 36px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: #94a3b8;
  font-size: 18px;
  line-height: 1;
}
.dn-del:hover {
  color: #ef4444;
}
.dn-del:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.dn-tag {
  display: inline-block;
  margin-right: 8px;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  font-size: 12px;
  font-weight: 700;
  vertical-align: middle;
}

.dn-modal {
  position: fixed;
  inset: 0;
  z-index: 220;
  margin: auto;
  width: min(720px, 92vw);
  max-height: min(70vh, 720px);
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 14px;
  box-shadow: 0 18px 60px rgba(15, 23, 42, 0.2);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.dn-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  font-weight: 700;
}
.dn-modal-body {
  padding: 14px 16px 16px;
  overflow: auto;
}
.dn-modal-title {
  margin: 0 0 6px;
  font-size: 18px;
  line-height: 1.3;
  color: #0f172a;
}
.dn-modal-meta {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
}
.dn-modal-content {
  white-space: pre-wrap;
  word-break: break-word;
  color: #334155;
  line-height: 1.65;
}
.dn-item-btn:hover {
  background: rgba(15, 23, 42, 0.04);
}
.dn-item-title {
  display: block;
  font-weight: 600;
  color: #0f172a;
  font-size: 0.92rem;
}
.dn-item-body {
  display: block;
  margin-top: 4px;
  color: #475569;
  font-size: 0.85rem;
  line-height: 1.4;
}
.dn-item-meta {
  display: block;
  margin-top: 6px;
  font-size: 0.75rem;
  color: #94a3b8;
}
</style>
