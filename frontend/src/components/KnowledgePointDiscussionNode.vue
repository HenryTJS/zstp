<script setup>
import { computed, ref } from 'vue'
import KnowledgePointDiscussionNode from './KnowledgePointDiscussionNode.vue'

const props = defineProps({
  post: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  currentUserId: { type: [Number, String], default: null },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle-like', 'submit-reply'])

const openReply = ref(false)
const replyLocal = ref('')

const roleLabel = (role) => {
  if (role === 'teacher') return '教师'
  if (role === 'student') return '学生'
  if (role === 'admin') return '管理员'
  return role || ''
}

const formatTime = (iso) => {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return String(iso)
  }
}

const sendReply = (postId) => {
  const t = (replyLocal.value || '').trim()
  if (!t) return
  emit('submit-reply', { parentId: postId, content: t })
  replyLocal.value = ''
  openReply.value = false
}

/** 仅根帖：整条讨论串下的回复总数（不含根帖自身） */
function threadReplyCount(p) {
  const list = p?.replies || []
  return list.reduce((acc, r) => acc + 1 + threadReplyCount(r), 0)
}

const replyPlaceholder = computed(() => {
  const name = props.post.author?.username || 'TA'
  return `回复 @${name}：`
})

const isRoot = computed(() => props.depth === 0)
</script>

<template>
  <div class="kp-disc-node" :style="{ marginLeft: depth ? Math.min(depth * 16, 72) + 'px' : '0' }">
    <div
      :id="'kp-disc-post-' + post.id"
      class="kp-disc-card"
      :class="{ 'kp-disc-card--nested': depth > 0 }"
    >
      <div class="kp-disc-meta">
        <span v-if="isRoot && post.postKind === 'QA'" class="kp-disc-kind-tag kp-disc-kind-tag--qa">答疑帖</span>
        <span v-if="isRoot && post.postKind === 'DISCUSSION'" class="kp-disc-kind-tag kp-disc-kind-tag--dc">讨论帖</span>
        <span class="kp-disc-author">{{ post.author?.username || '—' }}</span>
        <template v-if="post.replyTo?.username">
          <span class="kp-disc-reply-verb">回复</span>
          <span class="kp-disc-reply-target">@{{ post.replyTo.username }}</span>
          <span v-if="post.replyTo.role" class="kp-disc-reply-target-role"
            >（{{ roleLabel(post.replyTo.role) }}）</span>
        </template>
        <span class="kp-disc-role">{{ roleLabel(post.author?.role) }}</span>
        <span class="kp-disc-sep">·</span>
        <span class="kp-disc-time">{{ formatTime(post.createdAt) }}</span>
      </div>
      <div class="kp-disc-body">{{ post.content }}</div>
      <div class="kp-disc-actions">
        <button
          type="button"
          class="kp-disc-icon-btn kp-disc-like"
          :class="{ active: post.likedByMe }"
          :disabled="!currentUserId"
          :title="post.likedByMe ? '取消赞' : '点赞'"
          :aria-label="`点赞 ${post.likeCount ?? 0}`"
          @click="emit('toggle-like', post.id)"
        >
          <svg
            class="kp-disc-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path
              v-if="post.likedByMe"
              d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
              fill="currentColor"
              stroke="none"
            />
            <path
              v-else
              d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
              fill="none"
            />
          </svg>
          <span class="kp-disc-count">{{ post.likeCount ?? 0 }}</span>
        </button>
        <button
          type="button"
          class="kp-disc-icon-btn kp-disc-reply-btn"
          title="回复"
          :aria-label="isRoot ? `评论 ${threadReplyCount(post)}` : '回复'"
          @click="openReply = !openReply"
        >
          <svg
            class="kp-disc-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
          <span v-if="isRoot" class="kp-disc-count">{{ threadReplyCount(post) }}</span>
        </button>
      </div>
      <div v-if="openReply" class="kp-disc-reply-box">
        <textarea v-model="replyLocal" rows="2" :placeholder="replyPlaceholder" />
        <button
          type="button"
          class="match-button"
          :disabled="submitting || !replyLocal.trim() || !currentUserId"
          @click="sendReply(post.id)"
        >
          {{ submitting ? '发送中…' : '发表回复' }}
        </button>
      </div>
    </div>
    <KnowledgePointDiscussionNode
      v-for="r in post.replies || []"
      :key="r.id"
      :post="r"
      :depth="depth + 1"
      :current-user-id="currentUserId"
      :submitting="submitting"
      @toggle-like="$emit('toggle-like', $event)"
      @submit-reply="$emit('submit-reply', $event)"
    />
  </div>
</template>

<style scoped>
.kp-disc-node {
  margin-top: 10px;
}
.kp-disc-card {
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.6);
}
.kp-disc-card--nested {
  border-left: 3px solid #cbd5e1;
  background: rgba(248, 250, 252, 0.95);
}
.kp-disc-kind-tag {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
  letter-spacing: 0.02em;
}
.kp-disc-kind-tag--qa {
  background: rgba(234, 88, 12, 0.15);
  color: #c2410c;
}
.kp-disc-kind-tag--dc {
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
}
.kp-disc-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  align-items: center;
  font-size: 0.9em;
  margin-bottom: 8px;
  line-height: 1.4;
}
.kp-disc-author {
  font-weight: 600;
  color: #0f172a;
}
.kp-disc-reply-verb {
  color: #64748b;
  font-size: 0.92em;
}
.kp-disc-reply-target {
  font-weight: 600;
  color: #2563eb;
}
.kp-disc-reply-target-role {
  color: #94a3b8;
  font-size: 0.85em;
}
.kp-disc-role {
  color: #64748b;
  font-size: 0.85em;
}
.kp-disc-sep {
  color: #cbd5e1;
  user-select: none;
}
.kp-disc-time {
  color: #94a3b8;
  font-size: 0.85em;
}
.kp-disc-body {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  color: #1e293b;
}
.kp-disc-actions {
  margin-top: 10px;
  display: flex;
  gap: 6px 16px;
  flex-wrap: wrap;
  align-items: center;
}
.kp-disc-icon-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 8px;
  font-size: 0.875rem;
  color: #64748b;
  transition: background 0.15s ease, color 0.15s ease;
}
.student-lms-shell .kp-disc-icon-btn {
  background: transparent !important;
  border: none !important;
  color: #64748b !important;
  min-height: auto;
  padding: 6px 8px;
}
.student-lms-shell .kp-disc-icon-btn:hover:not(:disabled) {
  background: rgba(15, 23, 42, 0.06) !important;
  color: #0f172a !important;
}
.kp-disc-icon-btn:hover {
  background: rgba(15, 23, 42, 0.06);
  color: #0f172a;
}
.kp-disc-icon-btn:focus,
.kp-disc-icon-btn:focus-visible {
  outline: none;
  box-shadow: none;
}
.kp-disc-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}
.kp-disc-count {
  min-width: 1.25em;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #475569;
}
.kp-disc-icon-btn:hover .kp-disc-count {
  color: #0f172a;
}
.kp-disc-like.active {
  color: #dc2626;
}
.kp-disc-like.active .kp-disc-count {
  color: #dc2626;
}
.kp-disc-like:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.kp-disc-like:disabled:hover {
  background: transparent;
}
.kp-disc-reply-box {
  margin-top: 8px;
}
.kp-disc-reply-box textarea {
  width: 100%;
  box-sizing: border-box;
  margin-bottom: 6px;
  padding: 8px;
  border-radius: 6px;
  border: 1px solid #cbd5e1;
  font-family: inherit;
}
</style>
