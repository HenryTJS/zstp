<script setup>
import { computed, ref } from 'vue'
import KnowledgePointDiscussionNode from './KnowledgePointDiscussionNode.vue'

const props = defineProps({
  post: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  currentUserId: { type: [Number, String], default: null },
  submitting: { type: Boolean, default: false },
  /** 与父级一致：浏览模式等场景禁止操作 */
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle-like', 'submit-reply', 'delete-post'])

const openReply = ref(false)
const replyLocal = ref('')

const roleLabel = (role) => {
  if (role === 'teacher') return '教师'
  if (role === 'student') return '学生'
  if (role === 'admin') return '管理员'
  return role || ''
}

const formatTimeShort = (iso) => {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    const now = new Date()
    if (d.toDateString() === now.toDateString()) {
      return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    }
    return d.toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
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
  return `回复 @${name}…`
})

const isRoot = computed(() => props.depth === 0)

const isOwnPost = computed(() => {
  const uid = props.currentUserId
  const aid = props.post?.author?.id
  if (uid == null || uid === '' || aid == null || aid === '') return false
  return String(uid) === String(aid)
})

const authorInitial = computed(() => {
  const u = String(props.post.author?.username || '?').trim()
  return u ? u.charAt(0).toUpperCase() : '?'
})
</script>

<template>
  <div
    class="kp-disc-node"
    :class="{ 'kp-disc-node--nested': depth > 0 }"
    :style="{ '--kp-depth': String(Math.min(depth, 4)) }"
  >
    <div :id="'kp-disc-post-' + post.id" class="kp-disc-comment">
      <div class="kp-disc-avatar" :aria-hidden="true">{{ authorInitial }}</div>
      <div class="kp-disc-main">
        <div class="kp-disc-head">
          <span class="kp-disc-author">{{ post.author?.username || '—' }}</span>
          <span v-if="isRoot && post.postKind === 'QA'" class="kp-disc-chip kp-disc-chip--qa">答疑</span>
          <span v-if="isRoot && post.postKind === 'DISCUSSION'" class="kp-disc-chip kp-disc-chip--dc">讨论</span>
          <span v-if="post.replyTo?.username" class="kp-disc-reply-hint">
            回复 <span class="kp-disc-at">@{{ post.replyTo.username }}</span>
          </span>
          <span class="kp-disc-role-pill">{{ roleLabel(post.author?.role) }}</span>
          <span class="kp-disc-time">{{ formatTimeShort(post.createdAt) }}</span>
        </div>
        <div class="kp-disc-body">{{ post.content }}</div>
        <div class="kp-disc-actions">
          <button
            type="button"
            class="kp-disc-link-btn"
            :class="{ 'kp-disc-link-btn--on': post.likedByMe }"
            :disabled="!currentUserId"
            @click="emit('toggle-like', post.id)"
          >
            {{ post.likedByMe ? '已赞' : '点赞' }}
            <span v-if="(post.likeCount ?? 0) > 0" class="kp-disc-num">{{ post.likeCount }}</span>
          </button>
          <span class="kp-disc-dot">·</span>
          <button type="button" class="kp-disc-link-btn" @click="openReply = !openReply">
            回复<span v-if="isRoot && threadReplyCount(post) > 0" class="kp-disc-num">{{ threadReplyCount(post) }}</span>
          </button>
          <template v-if="isOwnPost && currentUserId && !disabled">
            <span class="kp-disc-dot">·</span>
            <button
              type="button"
              class="kp-disc-link-btn kp-disc-link-btn--danger"
              :disabled="submitting"
              @click="emit('delete-post', post.id)"
            >
              删除
            </button>
          </template>
        </div>
        <div v-if="openReply" class="kp-disc-reply-box">
          <textarea v-model="replyLocal" rows="2" :placeholder="replyPlaceholder" />
          <div class="kp-disc-reply-actions">
            <button type="button" class="kp-disc-reply-cancel" @click="openReply = false">取消</button>
            <button
              type="button"
              class="match-button kp-disc-reply-send"
              :disabled="submitting || !replyLocal.trim() || !currentUserId"
              @click="sendReply(post.id)"
            >
              {{ submitting ? '发送中…' : '发送' }}
            </button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="post.replies?.length" class="kp-disc-children">
      <KnowledgePointDiscussionNode
        v-for="r in post.replies || []"
        :key="r.id"
        :post="r"
        :depth="depth + 1"
        :current-user-id="currentUserId"
        :submitting="submitting"
        :disabled="disabled"
        @toggle-like="$emit('toggle-like', $event)"
        @submit-reply="$emit('submit-reply', $event)"
        @delete-post="$emit('delete-post', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.kp-disc-node {
  margin-top: 0;
}

.kp-disc-node--nested {
  margin-left: calc(8px + var(--kp-depth, 0) * 10px);
  padding-left: 10px;
  border-left: 2px solid #e2e8f0;
}

.kp-disc-comment {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.kp-disc-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  user-select: none;
}

.kp-disc-main {
  min-width: 0;
  flex: 1;
}

.kp-disc-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  font-size: 12px;
  line-height: 1.35;
  margin-bottom: 4px;
}

.kp-disc-author {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.kp-disc-chip {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
  letter-spacing: 0.02em;
}

.kp-disc-chip--qa {
  background: rgba(234, 88, 12, 0.12);
  color: #c2410c;
}

.kp-disc-chip--dc {
  background: rgba(79, 70, 229, 0.12);
  color: #4f46e5;
}

.kp-disc-reply-hint {
  color: #64748b;
  font-size: 12px;
}

.kp-disc-at {
  color: #4f46e5;
  font-weight: 600;
}

.kp-disc-role-pill {
  color: #94a3b8;
  font-size: 11px;
}

.kp-disc-time {
  color: #94a3b8;
  font-size: 11px;
  margin-left: auto;
}

.kp-disc-body {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.45;
  font-size: 13px;
  color: #334155;
}

.kp-disc-actions {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 0;
  font-size: 12px;
}

.kp-disc-link-btn {
  background: none;
  border: none;
  padding: 2px 4px;
  margin: 0;
  cursor: pointer;
  color: #64748b;
  font-size: 12px;
  border-radius: 4px;
  min-height: auto !important;
}

.student-lms-shell .kp-disc-link-btn {
  background: transparent !important;
  border: none !important;
  color: #64748b !important;
  min-height: auto !important;
  padding: 2px 4px !important;
}

.student-lms-shell .kp-disc-link-btn:hover:not(:disabled) {
  color: #4f46e5 !important;
  background: rgba(79, 70, 229, 0.06) !important;
}

.kp-disc-link-btn:hover:not(:disabled) {
  color: #4f46e5;
  background: rgba(79, 70, 229, 0.06);
}

.kp-disc-link-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.kp-disc-link-btn--on {
  color: #dc2626 !important;
}

.kp-disc-link-btn--danger:hover:not(:disabled) {
  color: #b91c1c !important;
  background: rgba(185, 28, 28, 0.08) !important;
}

.kp-disc-dot {
  color: #cbd5e1;
  user-select: none;
  padding: 0 2px;
}

.kp-disc-num {
  margin-left: 2px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #64748b;
}

.kp-disc-link-btn--on .kp-disc-num {
  color: #dc2626;
}

.kp-disc-reply-box {
  margin-top: 8px;
}

.kp-disc-reply-box textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.45;
  resize: vertical;
  min-height: 52px;
}

.kp-disc-reply-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.kp-disc-reply-cancel {
  background: none !important;
  border: none !important;
  color: #64748b !important;
  font-size: 12px !important;
  cursor: pointer;
  padding: 4px 8px !important;
  min-height: auto !important;
}

.kp-disc-reply-cancel:hover {
  color: #0f172a !important;
}

.kp-disc-reply-send {
  padding: 5px 14px !important;
  min-height: 32px !important;
  font-size: 13px !important;
}

.kp-disc-children {
  margin-top: 0;
}
</style>
