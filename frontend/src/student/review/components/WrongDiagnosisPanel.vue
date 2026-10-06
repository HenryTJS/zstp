<script setup>
import { computed, ref, watch } from 'vue'
import { fetchWrongDiagnosis } from '../../../api/client'

/**
 * 错题驱动闭环的前端面板：
 * 错题 → AI 错因分析 → 推荐资料 → 针对性练习 → 再次作答 → 掌握度变化
 *
 * 该组件自包含数据加载与练习判分，父组件只需传入课程/知识点与该知识点的错题列表。
 */
const props = defineProps({
  visible: { type: Boolean, default: false },
  courseName: { type: String, default: '' },
  knowledgePoint: { type: String, default: '' },
  items: { type: Array, default: () => [] },
  renderLatexText: { type: Function, default: (text) => String(text ?? '') }
})

const emit = defineEmits(['close'])

const loading = ref(false)
const error = ref('')
const result = ref(null)

/** 练习作答：index -> 学生答案 */
const answers = ref({})
const submitted = ref(false)
const gradingDetail = ref([])

const resetState = () => {
  answers.value = {}
  submitted.value = false
  gradingDetail.value = []
}

/** 拉取诊断结果（同一知识点 + 相同错题集合会命中后端缓存） */
const loadDiagnosis = async () => {
  if (!props.knowledgePoint) return
  loading.value = true
  error.value = ''
  result.value = null
  resetState()
  try {
    const { data } = await fetchWrongDiagnosis({
      courseName: props.courseName,
      knowledgePoint: props.knowledgePoint,
      wrongItems: props.items || []
    })
    result.value = data || null
  } catch (err) {
    error.value = err?.response?.data?.message || 'AI 诊断失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.visible, props.knowledgePoint, props.courseName],
  ([visible]) => {
    if (visible) loadDiagnosis()
  },
  { immediate: true }
)

const diagnosis = computed(() => result.value?.diagnosis || {})
const materials = computed(() => (Array.isArray(result.value?.recommendedMaterials) ? result.value.recommendedMaterials : []))
const practiceQuestions = computed(() =>
  Array.isArray(result.value?.practiceQuestions) ? result.value.practiceQuestions : []
)
const diagnosisSource = computed(() => String(result.value?.diagnosisSource || ''))
const isAiDiagnosis = computed(() => diagnosisSource.value === 'ai')

/** 选项字母：从 "A. xxx" 这类文本中提取 A */
const parseLetter = (option) => {
  const matched = String(option || '').trim().match(/^([A-Za-z])[.、．)）:：\s]/)
  return matched ? matched[1].toUpperCase() : ''
}

/** 答案字母：支持 "B"、"B. xxx"、"选B" 等写法 */
const answerLetter = (answer) => {
  const text = String(answer || '').trim().toUpperCase()
  const pure = text.match(/^([A-F]+)$/)
  if (pure) return pure[1]
  const matched = text.match(/([A-F])/)
  return matched ? matched[1] : ''
}

const normalizeText = (value) =>
  String(value || '')
    .toLowerCase()
    .replace(/[\s,，。.、;；:：'"“”‘’()（）]/g, '')

/** 本地判分：选择题比对字母，填空题做归一化包含匹配，避免额外消耗 AI 配额 */
const isCorrect = (question, answer) => {
  const student = String(answer ?? '').trim()
  if (!student) return false

  if (question?.question_type === '填空题') {
    const studentText = normalizeText(student)
    const reference = normalizeText(question.answer)
    if (!reference) return false
    return studentText === reference || studentText.includes(reference) || reference.includes(studentText)
  }

  return answerLetter(student) === answerLetter(question?.answer)
}

const correctCount = computed(() =>
  gradingDetail.value.filter((row) => row.correct).length
)

const accuracyBefore = computed(() => {
  const value = result.value?.accuracyBefore
  return typeof value === 'number' ? value : null
})

const accuracyAfter = computed(() => {
  if (!submitted.value || !gradingDetail.value.length) return null
  return correctCount.value / gradingDetail.value.length
})

const improvement = computed(() => {
  if (accuracyBefore.value == null || accuracyAfter.value == null) return null
  return accuracyAfter.value - accuracyBefore.value
})

const toPercent = (ratio) => (ratio == null ? '—' : `${Math.round(ratio * 100)}%`)

/** 练习是否参考了课程往年题的题型规格（后端返回 examStyleApplied / examStyleTitle） */
const examStyleHint = computed(() => {
  if (!result.value?.examStyleApplied) return ''
  const title = String(result.value.examStyleTitle || '').trim()
  return title
    ? `已参考《${title}》的题型结构与分值规格出题，更贴近真实考试`
    : '已参考课程期末试卷的题型结构与分值规格出题'
})

const submitPractice = () => {
  gradingDetail.value = practiceQuestions.value.map((question, index) => {
    const answer = answers.value[index] ?? ''
    return { index, answer, correct: isCorrect(question, answer) }
  })
  submitted.value = true
}

const restartPractice = () => resetState()

const setAnswer = (index, value) => {
  answers.value = { ...answers.value, [index]: value }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="diagnosis-mask" @click.self="emit('close')">
      <div class="diagnosis-modal" role="dialog" aria-label="AI 错因诊断">
        <header class="diagnosis-head">
          <div>
            <h3 class="diagnosis-title">AI 错因诊断与针对性练习</h3>
            <p class="diagnosis-sub">{{ courseName }} · {{ knowledgePoint }}</p>
          </div>
          <button type="button" class="diagnosis-close" @click="emit('close')">×</button>
        </header>

        <div class="diagnosis-body">
          <p v-if="loading" class="diagnosis-hint">正在分析错题并生成针对性练习…</p>
          <p v-else-if="error" class="diagnosis-error">{{ error }}</p>

          <template v-else-if="result">
            <!-- 步骤 1：错题概况 + 掌握度变化 -->
            <section class="diagnosis-block">
              <h4 class="diagnosis-block-title">① 错题定位</h4>
              <p class="diagnosis-text">
                本知识点共 <strong>{{ result.wrongCount }}</strong> 道错题；
                干预前得分率 <strong>{{ toPercent(accuracyBefore) }}</strong>
              </p>

              <div v-if="submitted && accuracyAfter != null" class="diagnosis-progress">
                <div class="progress-row">
                  <span class="progress-label">干预前</span>
                  <div class="progress-track">
                    <div class="progress-fill progress-fill--before" :style="{ width: `${(accuracyBefore ?? 0) * 100}%` }" />
                  </div>
                  <span class="progress-value">{{ toPercent(accuracyBefore) }}</span>
                </div>
                <div class="progress-row">
                  <span class="progress-label">干预后</span>
                  <div class="progress-track">
                    <div class="progress-fill progress-fill--after" :style="{ width: `${accuracyAfter * 100}%` }" />
                  </div>
                  <span class="progress-value">{{ toPercent(accuracyAfter) }}</span>
                </div>
                <p class="progress-delta" :class="{ 'progress-delta--up': (improvement ?? 0) > 0, 'progress-delta--down': (improvement ?? 0) < 0 }">
                  {{ (improvement ?? 0) > 0 ? '掌握度提升 ' : (improvement ?? 0) < 0 ? '掌握度下降 ' : '掌握度持平 ' }}
                  {{ improvement == null ? '—' : `${Math.abs(Math.round(improvement * 100))} 个百分点` }}
                </p>
              </div>
            </section>

            <!-- 步骤 2：AI 错因分析 -->
            <section class="diagnosis-block">
              <h4 class="diagnosis-block-title">② AI 错因分析</h4>
              <p v-if="!isAiDiagnosis" class="diagnosis-note">
                AI 诊断暂不可用，以下为基于错题记录的基础提示。
              </p>
              <div class="diagnosis-error-type">
                <span class="error-type-chip">{{ diagnosis.errorType || '概念不清' }}</span>
              </div>
              <p class="diagnosis-text"><strong>原因分析：</strong>{{ diagnosis.reason || '—' }}</p>
              <p v-if="diagnosis.misconception" class="diagnosis-text">
                <strong>具体误解点：</strong>{{ diagnosis.misconception }}
              </p>
              <ul v-if="(diagnosis.reviewPoints || []).length" class="diagnosis-list">
                <li v-for="(point, index) in diagnosis.reviewPoints" :key="`rp-${index}`">{{ point }}</li>
              </ul>
            </section>

            <!-- 步骤 3：推荐课程资料 -->
            <section class="diagnosis-block">
              <h4 class="diagnosis-block-title">③ 推荐复习资料</h4>
              <ul v-if="materials.length" class="diagnosis-materials">
                <li v-for="material in materials" :key="`mt-${material.id}`" class="material-row">
                  <span class="material-title">{{ material.title }}</span>
                  <span class="material-meta">{{ material.category || '资料' }} · {{ material.knowledgePoint || knowledgePoint }}</span>
                </li>
              </ul>
              <p v-else class="diagnosis-note">该知识点暂未上传课程资料，可先参考错题解析复习。</p>
            </section>

            <!-- 步骤 4：针对性练习与再次作答 -->
            <section class="diagnosis-block">
              <h4 class="diagnosis-block-title">④ 针对性练习</h4>
              <p v-if="examStyleHint" class="exam-style-hint">{{ examStyleHint }}</p>
              <p v-if="!practiceQuestions.length" class="diagnosis-note">
                本次未能生成练习题目（AI 不可用或输出不可用），可点击下方重新诊断。
              </p>

              <template v-else>
                <article v-for="(question, qIndex) in practiceQuestions" :key="`pq-${qIndex}`" class="practice-card">
                  <p class="practice-stem">
                    <span class="practice-index">{{ qIndex + 1 }}</span>
                    <span v-html="renderLatexText(question.question)" />
                  </p>

                  <div v-if="question.question_type === '选择题'" class="practice-options">
                    <label
                      v-for="option in question.options"
                      :key="`op-${qIndex}-${option}`"
                      class="practice-option"
                      :class="{
                        'practice-option--picked': answerLetter(answers[qIndex]) === parseLetter(option),
                        'practice-option--correct': submitted && answerLetter(question.answer) === parseLetter(option),
                        'practice-option--wrong':
                          submitted &&
                          answerLetter(answers[qIndex]) === parseLetter(option) &&
                          answerLetter(question.answer) !== parseLetter(option)
                      }"
                    >
                      <input
                        type="radio"
                        :name="`practice-${qIndex}`"
                        :value="option"
                        :checked="answers[qIndex] === option"
                        :disabled="submitted"
                        @change="setAnswer(qIndex, option)"
                      />
                      <span v-html="renderLatexText(option)" />
                    </label>
                  </div>

                  <div v-else class="practice-blank">
                    <input
                      type="text"
                      class="practice-input"
                      :value="answers[qIndex] || ''"
                      :disabled="submitted"
                      placeholder="请输入答案"
                      @input="setAnswer(qIndex, $event.target.value)"
                    />
                  </div>

                  <div v-if="submitted" class="practice-feedback">
                    <p class="practice-result" :class="gradingDetail[qIndex]?.correct ? 'is-right' : 'is-wrong'">
                      {{ gradingDetail[qIndex]?.correct ? '✓ 正确' : '✗ 错误' }}
                    </p>
                    <p class="practice-answer">参考答案：{{ question.answer }}</p>
                    <p v-if="question.explanation" class="practice-explanation">解析：{{ question.explanation }}</p>
                  </div>
                </article>

                <div class="practice-actions">
                  <button v-if="!submitted" type="button" class="match-button" @click="submitPractice">
                    提交练习并查看掌握度变化
                  </button>
                  <template v-else>
                    <span class="practice-score">本次答对 {{ correctCount }} / {{ practiceQuestions.length }} 题</span>
                    <button type="button" class="match-button practice-retry" @click="restartPractice">重做练习</button>
                  </template>
                </div>
              </template>

              <button type="button" class="diagnosis-refresh" :disabled="loading" @click="loadDiagnosis">
                重新诊断
              </button>
            </section>
          </template>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.diagnosis-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 1200;
}

.diagnosis-modal {
  width: min(820px, 100%);
  max-height: 88vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.25);
}

.diagnosis-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 22px;
  border-bottom: 1px solid #e5e7eb;
}

.diagnosis-title {
  margin: 0;
  font-size: 17px;
  color: #1f2937;
}

.diagnosis-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.diagnosis-close {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: #6b7280;
  cursor: pointer;
}

.diagnosis-body {
  padding: 18px 22px 24px;
  overflow-y: auto;
}

.diagnosis-block {
  margin-bottom: 22px;
}

.diagnosis-block-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: #4f46e5;
}

.diagnosis-text {
  margin: 6px 0;
  font-size: 14px;
  line-height: 1.7;
  color: #374151;
}

.diagnosis-hint {
  font-size: 14px;
  color: #6b7280;
}

.diagnosis-error {
  font-size: 14px;
  color: #dc2626;
}

.diagnosis-note {
  margin: 6px 0;
  font-size: 13px;
  color: #9ca3af;
}

/* 提示练习已对齐课程往年题的题型与分值 */
.exam-style-hint {
  margin: 6px 0 10px;
  padding: 7px 10px;
  border-radius: 8px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  font-size: 12.5px;
  line-height: 1.6;
}

.diagnosis-error-type {
  margin: 8px 0;
}

.error-type-chip {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 13px;
  font-weight: 600;
}

.diagnosis-list {
  margin: 8px 0 0;
  padding-left: 20px;
  font-size: 14px;
  color: #374151;
  line-height: 1.8;
}

.diagnosis-progress {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-label {
  width: 56px;
  font-size: 13px;
  color: #6b7280;
}

.progress-track {
  flex: 1;
  height: 10px;
  border-radius: 999px;
  background: #f1f5f9;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.4s ease;
}

.progress-fill--before {
  background: #fbbf24;
}

.progress-fill--after {
  background: #34d399;
}

.progress-value {
  width: 46px;
  text-align: right;
  font-size: 13px;
  color: #374151;
}

.progress-delta {
  margin: 2px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
}

.progress-delta--up {
  color: #059669;
}

.progress-delta--down {
  color: #dc2626;
}

.diagnosis-materials {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.material-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fafafa;
}

.material-title {
  font-size: 14px;
  color: #1f2937;
}

.material-meta {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}

.practice-card {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  margin-bottom: 12px;
}

.practice-stem {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.7;
  color: #1f2937;
}

.practice-index {
  display: inline-block;
  min-width: 20px;
  margin-right: 6px;
  font-weight: 700;
  color: #4f46e5;
}

.practice-options {
  display: grid;
  gap: 8px;
}

.practice-option {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 14px;
  cursor: pointer;
}

.practice-option--picked {
  border-color: #6366f1;
  background: #eef2ff;
}

.practice-option--correct {
  border-color: #34d399;
  background: #ecfdf5;
}

.practice-option--wrong {
  border-color: #f87171;
  background: #fef2f2;
}

.practice-input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  font-size: 14px;
}

.practice-feedback {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e5e7eb;
}

.practice-result {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 700;
}

.practice-result.is-right {
  color: #059669;
}

.practice-result.is-wrong {
  color: #dc2626;
}

.practice-answer,
.practice-explanation {
  margin: 3px 0;
  font-size: 13px;
  color: #4b5563;
  line-height: 1.7;
}

.practice-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
}

.practice-score {
  font-size: 14px;
  font-weight: 600;
  color: #4338ca;
}

.practice-retry {
  background: #6b7280;
}

.diagnosis-refresh {
  margin-top: 14px;
  border: none;
  background: transparent;
  color: #4f46e5;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}

.diagnosis-refresh:disabled {
  color: #9ca3af;
  cursor: not-allowed;
}
</style>
