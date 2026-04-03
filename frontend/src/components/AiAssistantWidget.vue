<script setup>
import { ref } from 'vue'
import { askAiAgent } from '../api/client'

const props = defineProps({
  role: {
    type: String,
    default: 'user'
  },
  currentUser: {
    type: Object,
    default: () => ({})
  }
})

const visible = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref([
  { role: 'assistant', content: '你好，我是 AI 助手。你可以直接问我平台使用、学习或教学相关问题。' }
])

const escapeHtml = (raw) =>
  String(raw || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

// Lightweight and safe markdown renderer for assistant messages.
const renderMarkdown = (raw) => {
  const escaped = escapeHtml(raw).replace(/\r\n/g, '\n')

  const withCode = escaped.replace(/```([\s\S]*?)```/g, (_, code) => {
    return `<pre class="md-pre"><code>${code.trim()}</code></pre>`
  })

  const withInline = withCode
    .replace(/`([^`\n]+)`/g, '<code class="md-code">$1</code>')
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*\n]+)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')

  const lines = withInline.split('\n')
  let inList = false
  const out = []
  for (const line of lines) {
    if (/^\s*-\s+/.test(line)) {
      if (!inList) {
        out.push('<ul>')
        inList = true
      }
      out.push(`<li>${line.replace(/^\s*-\s+/, '')}</li>`)
      continue
    }
    if (inList) {
      out.push('</ul>')
      inList = false
    }
    if (!line.trim()) {
      out.push('<br/>')
    } else {
      out.push(`<p>${line}</p>`)
    }
  }
  if (inList) out.push('</ul>')
  return out.join('')
}

const send = async () => {
  const text = (input.value || '').trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  try {
    const { data } = await askAiAgent({
      question: text,
      role: props.role,
      userId: props.currentUser?.id != null ? String(props.currentUser.id) : '',
      username: props.currentUser?.username || ''
    })
    messages.value.push({
      role: 'assistant',
      content: (data && data.answer) ? String(data.answer) : '暂时没有可用回复，请稍后再试。'
    })
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      content: e?.response?.data?.message || e?.message || '请求失败，请稍后重试。'
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <button class="ai-fab" type="button" @click="visible = true" aria-label="打开AI助手">🤖</button>

  <div v-if="visible" class="ai-mask" @click.self="visible = false">
    <div class="ai-modal">
      <div class="ai-head">
        <h3>AI 智能体</h3>
        <button type="button" class="ai-close" @click="visible = false">×</button>
      </div>
      <div class="ai-list">
        <div v-for="(m, idx) in messages" :key="idx" :class="['ai-item', m.role === 'user' ? 'is-user' : 'is-ai']">
          <template v-if="m.role === 'assistant'">
            <div class="md-content" v-html="renderMarkdown(m.content)"></div>
          </template>
          <template v-else>
            {{ m.content }}
          </template>
        </div>
      </div>
      <div class="ai-input-row">
        <textarea
          v-model="input"
          rows="3"
          :disabled="loading"
          placeholder="输入你的问题..."
          @keydown.enter.exact.prevent="send"
        ></textarea>
        <button type="button" class="match-button" :disabled="loading || !input.trim()" @click="send">
          {{ loading ? '思考中…' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-fab {
  position: fixed;
  right: 20px;
  bottom: 20px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  font-size: 24px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.2);
  z-index: 1100;
}

.ai-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1200;
}

.ai-modal {
  width: min(760px, calc(100vw - 32px));
  max-height: min(80vh, 760px);
  background: #fff;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #eee;
}

.ai-head h3 {
  margin: 0;
}

.ai-close {
  width: 30px;
  height: 30px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #fff;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.ai-close:hover {
  background: #f3f4f6;
}

.ai-list {
  padding: 12px;
  overflow: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fafafa;
}

.ai-item {
  max-width: 80%;
  padding: 10px 12px;
  border-radius: 10px;
  line-height: 1.5;
}

.ai-item.is-user {
  margin-left: auto;
  background: #dbeafe;
}

.ai-item.is-ai {
  margin-right: auto;
  background: #fff;
  border: 1px solid #eee;
}

.ai-input-row {
  padding: 12px;
  border-top: 1px solid #eee;
  display: flex;
  gap: 10px;
}

.ai-input-row textarea {
  flex: 1;
  resize: vertical;
  min-height: 72px;
}

.md-content :deep(p) {
  margin: 0 0 8px 0;
}

.md-content :deep(p:last-child) {
  margin-bottom: 0;
}

.md-content :deep(ul) {
  margin: 0 0 8px 18px;
}

.md-content :deep(li) {
  margin: 2px 0;
}

.md-content :deep(.md-code) {
  background: #f3f4f6;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: Consolas, monospace;
}

.md-content :deep(.md-pre) {
  margin: 8px 0;
  background: #111827;
  color: #f9fafb;
  padding: 10px;
  border-radius: 8px;
  overflow: auto;
}

.md-content :deep(a) {
  color: #2563eb;
  text-decoration: underline;
}
</style>
