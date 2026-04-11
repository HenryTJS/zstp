<script setup>
import { nextTick, ref, watch } from 'vue'
import {
  listKnowledgePointDiscussions,
  createKnowledgePointDiscussionPost,
  toggleKnowledgePointDiscussionLike,
  deleteKnowledgePointDiscussionPost
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
  disabled: { type: Boolean, default: false },
  /** 嵌入知识点详情分块：去掉顶部分割线与多余外边距 */
  embedded: { type: Boolean, default: false },
  /** 由外层区块展示标题时隐藏内部「交流区」标题 */
  hideTitle: { type: Boolean, default: false }
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

const onDeletePost = async (postId) => {
  if (!props.currentUserId || props.disabled || postId == null) return
  if (!confirm('确定删除这条发言吗？若其下还有回复，将一并删除。')) return
  submitting.value = true
  error.value = ''
  try {
    await deleteKnowledgePointDiscussionPost(postId, props.currentUserId)
    await load()
  } catch (e) {
    error.value = e?.response?.data?.message || '删除失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="kp-discussion-wrap" :class="{ 'kp-discussion-wrap--embedded': embedded }">
    <h3 v-if="!hideTitle">交流区</h3>
    <p v-if="disabled" class="panel-subtitle kp-disc-hint">当前为浏览模式，加入课程后可参与讨论。</p>
    <p v-if="error" class="error-text kp-disc-hint">{{ error }}</p>
    <div v-if="!disabled && currentUserId" class="kp-disc-composer">
      <div v-if="canPickQa() || canPickDiscussion()" class="kp-disc-kind-row">
        <template v-if="canPickQa()">
          <label class="kp-disc-kind-opt">
            <input v-model="rootPostKind" type="radio" value="NORMAL" />
            <span>评论</span>
          </label>
          <label class="kp-disc-kind-opt">
            <input v-model="rootPostKind" type="radio" value="QA" />
            <span>答疑</span>
          </label>
        </template>
        <template v-else-if="canPickDiscussion()">
          <label class="kp-disc-kind-opt">
            <input v-model="rootPostKind" type="radio" value="NORMAL" />
            <span>评论</span>
          </label>
          <label class="kp-disc-kind-opt">
            <input v-model="rootPostKind" type="radio" value="DISCUSSION" />
            <span>讨论</span>
          </label>
        </template>
      </div>
      <div class="kp-disc-composer-row">
        <textarea
          v-model="newPostContent"
          class="kp-disc-composer-input"
          rows="2"
          placeholder="写点什么…"
        />
        <button
          type="button"
          class="match-button kp-disc-publish-btn"
          :disabled="submitting || !newPostContent.trim()"
          @click="submitRoot"
        >
          {{ submitting ? '…' : '发布' }}
        </button>
      </div>
    </div>
    <div v-else-if="!disabled && !currentUserId" class="panel-subtitle kp-disc-hint">登录后可参与评论。</div>

    <p v-if="loading" class="panel-subtitle kp-disc-list-meta">加载中…</p>
    <div v-else-if="!threads.length" class="panel-subtitle kp-disc-list-meta kp-disc-empty">暂无评论，来抢沙发吧</div>
    <div v-else class="kp-disc-threads kp-disc-threads--scroll">
      <KnowledgePointDiscussionNode
        v-for="p in threads"
        :key="p.id"
        :post="p"
        :depth="0"
        :current-user-id="currentUserId"
        :submitting="submitting"
        :disabled="disabled"
        @toggle-like="onToggleLike"
        @submit-reply="onReply"
        @delete-post="onDeletePost"
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
.kp-discussion-wrap--embedded {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.kp-disc-hint {
  margin-bottom: 8px !important;
  font-size: 13px !important;
}

.kp-disc-composer {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
  margin-bottom: 8px;
}

.kp-disc-composer-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.kp-disc-composer-input {
  flex: 1;
  min-width: 0;
  box-sizing: border-box;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.45;
  resize: vertical;
  min-height: 52px;
  background: #fff;
}

.kp-disc-publish-btn {
  flex-shrink: 0;
  padding: 8px 16px !important;
  min-height: 36px !important;
  font-size: 13px !important;
}

.kp-disc-threads {
  margin-top: 0;
}

.kp-disc-threads--scroll {
  max-height: min(360px, 42vh);
  overflow-y: auto;
  padding-right: 4px;
  margin-right: -4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(15, 23, 42, 0.2) transparent;
}

.kp-disc-threads--scroll::-webkit-scrollbar {
  width: 6px;
}
.kp-disc-threads--scroll::-webkit-scrollbar-thumb {
  background: rgba(15, 23, 42, 0.18);
  border-radius: 999px;
}

.kp-disc-list-meta {
  margin-top: 8px !important;
  margin-bottom: 4px !important;
  font-size: 12px !important;
  color: #64748b !important;
}

.kp-disc-empty {
  padding: 12px 0;
  text-align: center;
}

.kp-disc-kind-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #64748b;
}
.kp-disc-kind-opt {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

@media (max-width: 560px) {
  .kp-disc-composer-row {
    flex-direction: column;
    align-items: stretch;
  }
  .kp-disc-publish-btn {
    width: 100%;
  }
}

:deep(.kp-disc-focus-flash) {
  animation: kp-disc-flash 2s ease;
}
@keyframes kp-disc-flash {
  0%,
  55% {
    background: rgba(99, 102, 241, 0.12);
    box-shadow: inset 0 0 0 1px rgba(99, 102, 241, 0.28);
    border-radius: 8px;
  }
  100% {
    background: transparent;
    box-shadow: none;
  }
}
</style>
