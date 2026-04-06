<script setup>
import { nextTick, ref, watch } from 'vue'
import {
  listKnowledgePointDiscussions,
  createKnowledgePointDiscussionPost,
  toggleKnowledgePointDiscussionLike
} from '../api/client'
import KnowledgePointDiscussionNode from './KnowledgePointDiscussionNode.vue'
import './student-portal.css'

const props = defineProps({
  courseName: { type: String, default: '' },
  pointName: { type: String, default: '' },
  currentUserId: { type: [Number, String], default: null },
  /** student | teacher | admin — 根帖类型 */
  userRole: { type: String, default: '' },
  /** 通知跳转：滚动并高亮对应帖子卡片 */
  focusPostId: { type: [Number, String], default: null },
  /** 浏览模式等场景禁止发帖 */
  disabled: { type: Boolean, default: false }
})

const threads = ref([])
const loading = ref(false)
const error = ref('')
const newPostContent = ref('')
const rootPostKind = ref('NORMAL')
const submitting = ref(false)

const canPickQa = () => props.userRole === 'student'
const canPickDiscussion = () => props.userRole === 'teacher' || props.userRole === 'admin'

const load = async () => {
  const cn = (props.courseName || '').trim()
  const pn = (props.pointName || '').trim()
  if (!cn || !pn) {
    threads.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    const { data } = await listKnowledgePointDiscussions({
      courseName: cn,
      pointName: pn,
      userId: props.currentUserId || undefined
    })
    threads.value = Array.isArray(data) ? data : []
  } catch (e) {
    const d = e?.response?.data
    error.value =
      (typeof d === 'string' && d) ||
      d?.message ||
      d?.error ||
      (e?.response?.status ? `请求失败（HTTP ${e.response.status}）` : '') ||
      e?.message ||
      '加载交流区失败'
    threads.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.courseName, props.pointName, props.currentUserId],
  () => {
    void load()
  },
  { immediate: true }
)

watch(
  () => props.userRole,
  (r) => {
    if (r === 'student' && rootPostKind.value === 'DISCUSSION') rootPostKind.value = 'NORMAL'
    if ((r === 'teacher' || r === 'admin') && rootPostKind.value === 'QA') rootPostKind.value = 'NORMAL'
  },
  { immediate: true }
)

const scrollToFocusedPost = async () => {
  const id = props.focusPostId
  if (id == null || id === '') return
  await nextTick()
  const el = document.getElementById(`kp-disc-post-${id}`)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.add('kp-disc-focus-flash')
  window.setTimeout(() => el.classList.remove('kp-disc-focus-flash'), 2000)
}

watch([threads, () => props.focusPostId, () => loading.value], async () => {
  if (loading.value || !threads.value.length) return
  if (props.focusPostId == null || props.focusPostId === '') return
  await scrollToFocusedPost()
})

const submitRoot = async () => {
  const content = (newPostContent.value || '').trim()
  if (!content || !props.currentUserId || props.disabled) return
  submitting.value = true
  error.value = ''
  try {
    const kind =
      canPickQa() && rootPostKind.value === 'QA'
        ? 'QA'
        : canPickDiscussion() && rootPostKind.value === 'DISCUSSION'
          ? 'DISCUSSION'
          : 'NORMAL'
    await createKnowledgePointDiscussionPost({
      userId: props.currentUserId,
      courseName: props.courseName.trim(),
      pointName: props.pointName.trim(),
      content,
      parentId: null,
      postKind: kind
    })
    newPostContent.value = ''
    rootPostKind.value = 'NORMAL'
    await load()
  } catch (e) {
    error.value = e?.response?.data?.message || '发帖失败'
  } finally {
    submitting.value = false
  }
}

const onReply = async ({ parentId, content }) => {
  if (!props.currentUserId || props.disabled) return
  submitting.value = true
  error.value = ''
  try {
    await createKnowledgePointDiscussionPost({
      userId: props.currentUserId,
      courseName: props.courseName.trim(),
      pointName: props.pointName.trim(),
      content,
      parentId
    })
    await load()
  } catch (e) {
    error.value = e?.response?.data?.message || '回复失败'
  } finally {
    submitting.value = false
  }
}

const onToggleLike = async (postId) => {
  if (!props.currentUserId || props.disabled) return
  try {
    await toggleKnowledgePointDiscussionLike(postId, { userId: props.currentUserId })
    await load()
  } catch (e) {
    error.value = e?.response?.data?.message || '点赞失败'
  }
}
</script>

<template>
  <div class="kp-discussion-wrap">
    <h3>交流区</h3>
    <p v-if="disabled" class="panel-subtitle">当前为浏览模式，加入课程后可参与讨论。</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <div v-if="!disabled && currentUserId" class="kp-disc-new">
      <div v-if="canPickQa() || canPickDiscussion()" class="kp-disc-kind-row">
        <template v-if="canPickQa()">
          <label class="kp-disc-kind-opt">
            <input v-model="rootPostKind" type="radio" value="NORMAL" />
            <span>正常帖</span>
          </label>
          <label class="kp-disc-kind-opt">
            <input v-model="rootPostKind" type="radio" value="QA" />
            <span>答疑帖</span>
          </label>
        </template>
        <template v-else-if="canPickDiscussion()">
          <label class="kp-disc-kind-opt">
            <input v-model="rootPostKind" type="radio" value="NORMAL" />
            <span>正常帖</span>
          </label>
          <label class="kp-disc-kind-opt">
            <input v-model="rootPostKind" type="radio" value="DISCUSSION" />
            <span>讨论帖</span>
          </label>
        </template>
      </div>
      <textarea v-model="newPostContent" rows="3" placeholder="发表新帖…" />
      <button
        type="button"
        class="match-button"
        style="margin-top: 8px"
        :disabled="submitting || !newPostContent.trim()"
        @click="submitRoot"
      >
        {{ submitting ? '发送中…' : '发帖' }}
      </button>
    </div>
    <div v-else-if="!disabled && !currentUserId" class="panel-subtitle">请登录后发帖。</div>

    <p v-if="loading" class="panel-subtitle" style="margin-top: 12px">加载中…</p>
    <div v-else-if="!threads.length" class="panel-subtitle" style="margin-top: 12px">暂无帖子，来发第一条吧。</div>
    <div v-else class="kp-disc-threads">
      <KnowledgePointDiscussionNode
        v-for="p in threads"
        :key="p.id"
        :post="p"
        :depth="0"
        :current-user-id="currentUserId"
        :submitting="submitting"
        @toggle-like="onToggleLike"
        @submit-reply="onReply"
      />
    </div>
  </div>
</template>

<style scoped>
.kp-discussion-wrap {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
}
.kp-disc-new textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  font-family: inherit;
}
.kp-disc-threads {
  margin-top: 12px;
}
.kp-disc-kind-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  margin-bottom: 8px;
  font-size: 0.9rem;
  color: #334155;
}
.kp-disc-kind-opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
:deep(.kp-disc-focus-flash) {
  animation: kp-disc-flash 2s ease;
}
@keyframes kp-disc-flash {
  0%,
  55% {
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.4);
  }
  100% {
    box-shadow: none;
  }
}
</style>
