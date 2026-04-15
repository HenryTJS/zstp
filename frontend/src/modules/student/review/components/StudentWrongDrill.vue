<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  session: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
  error: { type: String, default: '' },
  renderLatexText: { type: Function, required: true },
  parseOptionLetter: { type: Function, required: true },
  parseOptionText: { type: Function, required: true },
  inferQuestionType: { type: Function, required: true }
})

const emit = defineEmits(['cancel', 'submit'])

const answers = ref([])
const formHint = ref('')

const resetAnswers = (items) => {
  const list = Array.isArray(items) ? items : []
  answers.value = list.map((item) => {
    const qt = props.inferQuestionType(item)
    if (qt === '多选题') return []
    return ''
  })
}

watch(
  () => props.session,
  (s) => {
    formHint.value = ''
    resetAnswers(s?.items)
  },
  { immediate: true }
)

const questionType = (item) => props.inferQuestionType(item)

const validateAndSubmit = () => {
  formHint.value = ''
  const items = props.session?.items || []
  for (let i = 0; i < items.length; i++) {
    const qt = questionType(items[i])
    const a = answers.value[i]
    if (qt === '多选题') {
      if (!Array.isArray(a) || !a.length) {
        formHint.value = '请完成每一题的作答后再提交。'
        return
      }
    } else {
      if (!String(a ?? '').trim()) {
        formHint.value = '请完成每一题的作答后再提交。'
        return
      }
    }
  }
  emit('submit', answers.value.slice())
}
</script>

<template>
  <article v-if="session && session.items?.length" class="result-card wrong-drill-card">
    <h3 class="portal-section-title portal-section-title--orange">错题巩固测试</h3>
    <p v-if="error" class="error-text">{{ error }}</p>
    <p v-else-if="formHint" class="error-text">{{ formHint }}</p>

    <div v-for="(item, idx) in session.items" :key="item.id + '-drill'" class="wrong-drill-q ui-mt-14">
      <h4>第 {{ idx + 1 }} 题（{{ questionType(item) }} · {{ item.fullScore ?? 10 }} 分）</h4>
      <p class="panel-subtitle">知识点：{{ item.knowledgePoint || '—' }}</p>
      <div class="latex-block" v-html="renderLatexText(item.question)"></div>

      <div v-if="questionType(item) === '选择题'" class="option-list">
        <label v-for="opt in item.options || []" :key="'c-' + idx + '-' + opt" class="option-item">
          <input
            type="radio"
            :name="'wd-q-' + idx"
            :value="parseOptionLetter(opt)"
            v-model="answers[idx]"
          />
          <span v-html="renderLatexText(parseOptionText(opt))"></span>
        </label>
      </div>

      <div v-else-if="questionType(item) === '多选题'" class="option-list">
        <label v-for="opt in item.options || []" :key="'m-' + idx + '-' + opt" class="option-item">
          <input type="checkbox" :value="parseOptionLetter(opt)" v-model="answers[idx]" />
          <span v-html="renderLatexText(parseOptionText(opt))"></span>
        </label>
      </div>

      <div v-else-if="questionType(item) === '判断题'" class="option-list">
        <label v-for="opt in item.options || []" :key="'j-' + idx + '-' + opt" class="option-item">
          <input
            type="radio"
            :name="'wd-q-' + idx"
            :value="parseOptionLetter(opt)"
            v-model="answers[idx]"
          />
          <span v-html="renderLatexText(parseOptionText(opt))"></span>
        </label>
      </div>

      <div v-else-if="questionType(item) === '填空题'">
        <label>
          作答
          <input v-model="answers[idx]" class="match-height" placeholder="请输入答案" />
        </label>
      </div>

      <div v-else>
        <label>
          作答
          <textarea v-model="answers[idx]" rows="4" class="wrong-drill-textarea" placeholder="请输入答案"></textarea>
        </label>
      </div>
    </div>

    <div class="inline-form ui-mt-14">
      <button type="button" class="cancel-button" :disabled="submitting" @click="emit('cancel')">取消</button>
      <button type="button" class="match-button" :disabled="submitting" @click="validateAndSubmit">
        {{ submitting ? '提交中…' : '提交批改' }}
      </button>
    </div>
  </article>
</template>

<style scoped>
.wrong-drill-textarea {
  width: 100%;
  box-sizing: border-box;
  margin-top: 6px;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  font-family: inherit;
}
.option-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.option-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
}
</style>
