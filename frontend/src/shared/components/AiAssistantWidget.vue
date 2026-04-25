<script setup>
import { ref } from 'vue'
import { askAiAgent } from '../../api/client'
import MarkdownIt from 'markdown-it'
import mk from 'markdown-it-katex'

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
  {
    role: 'assistant',
    content:
      '你好，我是 **AI智能教学助手工作台** 内置助手。你可以随时问我知识点（如“什么是正态分布”），我会先直接讲清楚；如果你想了解平台怎么操作，我也可以一步步带你完成。'
  }
])

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})
  .use(mk, { throwOnError: false, errorColor: '#cc0000' })

const renderMarkdown = (raw) => md.render(String(raw || ''))

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
  <button class="ai-fab" type="button" @click="visible = true" aria-label="打开 AI 学习助手">🤖</button>

  <div v-if="visible" class="modal-mask" @click.self="visible = false">
    <div class="modal-wrapper" style="max-width: 760px; width: 94vw">
      <div class="modal-container ai-chat-shell" style="max-height: 88vh; overflow: hidden; display: flex; flex-direction: column">
        <button class="modal-close" type="button" aria-label="关闭" @click="visible = false">×</button>
        <h3 class="portal-section-title portal-section-title--violet">AI 学习助手</h3>
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
          <div class="ai-input-wrap">
            <textarea
              v-model="input"
              class="ai-chat-textarea"
              rows="3"
              :disabled="loading"
              placeholder="可直接问知识点，或咨询平台功能…"
              @keydown.enter.exact.prevent="send"
            ></textarea>
            <button type="button" class="match-button ai-send-inside" :disabled="loading || !input.trim()" @click="send">
              {{ loading ? '思考中…' : '发送' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-fab {
  position: fixed;
  right: max(16px, env(safe-area-inset-right, 0px));
  bottom: max(16px, env(safe-area-inset-bottom, 0px));
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: 1px solid rgba(15, 23, 42, 0.08);
  cursor: pointer;
  font-size: 24px;
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  box-shadow: 0 8px 24px -4px rgba(79, 70, 229, 0.45), 0 4px 12px rgba(15, 23, 42, 0.12);
  z-index: 1100;
}

.ai-fab:hover {
  filter: brightness(1.05);
}

.ai-chat-shell h3.portal-section-title {
  margin: 0;
  padding-right: 28px;
}

.ai-list {
  margin-top: 14px;
  padding: 12px;
  overflow: auto;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.06);
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
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.ai-input-wrap {
  width: 100%;
  position: relative;
}

.ai-chat-textarea {
  width: 100%;
  resize: none;
  min-height: 96px;
  padding: 12px 96px 12px 12px;
  border-radius: var(--ui-btn-radius, 10px);
  border: 1px solid rgba(15, 23, 42, 0.12);
  font: inherit;
  line-height: 1.4;
  box-sizing: border-box;
}

.ai-send-inside {
  position: absolute;
  right: 8px;
  bottom: 8px;
  z-index: 1;
}

.ai-chat-textarea:focus {
  outline: none;
  border-color: rgba(79, 70, 229, 0.45);
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15);
}

.ai-chat-textarea:disabled {
  opacity: 0.65;
  cursor: not-allowed;
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

.md-content :deep(.katex-display) {
  margin: 10px 0;
  overflow-x: auto;
  overflow-y: hidden;
}
</style>
<style>
@import '@/student/styles/student-portal.css';
</style>
