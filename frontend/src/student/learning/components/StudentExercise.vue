<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  canStudyCurrentCourse: { type: Boolean, required: true },
  selectedKnowledgePoint: { type: String, default: '' },
  questionForm: { type: Object, required: true },

  // test
  testLoading: { type: Boolean, required: true },
  testError: { type: String, required: true },
  testQuestions: { type: Array, required: true },
  testSubmitted: { type: Boolean, required: true },
  testResult: { type: Object, default: null },
  testAnswers: { type: Array, required: true },
  testCounts: { type: Object, default: () => ({ singleChoiceCount: 0, multiChoiceCount: 0, judgeCount: 0, fillCount: 0 }) },
  testForm: { type: Object, default: () => ({ selectedPoints: [] }) },

  // knowledge point options for multi-select
  kpOptions: { type: Array, default: () => [] },

  // actions
  generateTest: { type: Function, required: true },
  submitTest: { type: Function, required: true },
  onGoCourses: { type: Function, default: null },

  // render helpers
  renderLatexText: { type: Function, required: true },
  parseOptionLetter: { type: Function, required: true },
  parseOptionText: { type: Function, required: true },
  resolveAnswerText: { type: Function, required: true }
})

const emit = defineEmits(['go-courses', 'regenerate', 'add-test-point', 'remove-test-point'])

const selectedKpToAdd = ref('')

const goCourses = () => {
  if (props.onGoCourses) props.onGoCourses()
  else emit('go-courses')
}

const testTotalCount = computed(
  () =>
    Number(props.testCounts.singleChoiceCount || 0) +
    Number(props.testCounts.multiChoiceCount || 0) +
    Number(props.testCounts.judgeCount || 0) +
    Number(props.testCounts.fillCount || 0)
)
</script>

<template>
  <section class="panel-stack">
    <article v-if="!canStudyCurrentCourse" class="result-card">
      <h3 class="portal-section-title portal-section-title--amber">暂不可学习</h3>
      <button type="button" class="match-button" @click="goCourses">去课程广场</button>
    </article>

    <template v-else>
      <article class="result-card">
        <h3 class="portal-section-title portal-section-title--teal">出题与做题</h3>

        <div v-if="selectedKnowledgePoint">
          <div class="grid-form ui-mt-12">
            <label>
              难度
              <select v-model="questionForm.difficulty" class="match-height">
                <option>基础</option>
                <option>中等</option>
                <option>拔高</option>
              </select>
            </label>
          </div>

          <!-- 知识点多选 -->
          <div class="kp-multi-select ui-mt-12">
            <label class="kp-multi-select-label">知识点（可多选）</label>
            <div class="kp-multi-tags">
              <span
                v-for="kp in testForm.selectedPoints || []"
                :key="kp"
                class="kp-tag"
              >
                {{ kp }}
                <button type="button" class="kp-tag-remove" @click="emit('remove-test-point', kp)">&times;</button>
              </span>
            </div>
            <div class="kp-add-row">
              <select v-model="selectedKpToAdd" class="match-height" style="flex:1">
                <option value="">— 添加知识点 —</option>
                <option v-for="opt in kpOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
              <button type="button" class="cancel-button" :disabled="!selectedKpToAdd" @click="selectedKpToAdd ? (emit('add-test-point', selectedKpToAdd), selectedKpToAdd = '') : null">添加</button>
            </div>
          </div>

          <!-- 题型数量选择 -->
          <div class="test-count-grid ui-mt-12">
            <label>
              单选题
              <input type="number" min="0" max="10" v-model.number="testCounts.singleChoiceCount" class="match-height test-count-input" />
            </label>
            <label>
              多选题
              <input type="number" min="0" max="10" v-model.number="testCounts.multiChoiceCount" class="match-height test-count-input" />
            </label>
            <label>
              判断题
              <input type="number" min="0" max="10" v-model.number="testCounts.judgeCount" class="match-height test-count-input" />
            </label>
            <label>
              填空题
              <input type="number" min="0" max="10" v-model.number="testCounts.fillCount" class="match-height test-count-input" />
            </label>
          </div>
          <p class="panel-subtitle" style="margin-top: 4px">共 {{ testTotalCount }} 题（最多 10 题）</p>

          <div class="inline-form ui-mt-12">
            <button type="button" class="match-button" :disabled="testLoading || testTotalCount < 1" @click="generateTest">
              {{ testLoading ? '生成中...' : '开始测试' }}
            </button>
          </div>

          <p v-if="testError && !(testQuestions.length && !testSubmitted)" class="error-text">{{ testError }}</p>
        </div>
      </article>

      <article
        v-if="testQuestions.length && !testSubmitted"
        class="result-card"
      >
        <h3 class="portal-section-title portal-section-title--violet">测试题目</h3>

        <div v-for="(q, idx) in testQuestions" :key="idx" class="ui-mt-14">
          <h4>第 {{ idx + 1 }} 题（{{ q.question_type }}）</h4>
          <div class="latex-block" v-html="renderLatexText(q.question)"></div>

          <div v-if="q.question_type === '选择题'" class="option-list">
            <label v-for="opt in q.options || []" :key="opt" class="option-item">
              <input
                type="radio"
                :name="'test-q-' + idx"
                :value="parseOptionLetter(opt)"
                v-model="testAnswers[idx]"
              />
              <span v-html="renderLatexText(parseOptionText(opt))"></span>
            </label>
          </div>

          <div v-else-if="q.question_type === '多选题'" class="option-list">
            <label v-for="opt in q.options || []" :key="opt" class="option-item">
              <input
                type="checkbox"
                :value="parseOptionLetter(opt)"
                v-model="testAnswers[idx]"
              />
              <span v-html="renderLatexText(parseOptionText(opt))"></span>
            </label>
          </div>

          <div v-else-if="q.question_type === '判断题'" class="option-list">
            <label v-for="opt in q.options || []" :key="opt" class="option-item">
              <input
                type="radio"
                :name="'test-q-' + idx"
                :value="parseOptionLetter(opt)"
                v-model="testAnswers[idx]"
              />
              <span v-html="renderLatexText(parseOptionText(opt))"></span>
            </label>
          </div>

          <div v-else-if="q.question_type === '填空题'">
            <label>
              填空答案
              <input class="match-height" v-model="testAnswers[idx]" placeholder="请输入答案" />
            </label>
          </div>
        </div>

        <div class="inline-form ui-mt-12">
          <button type="button" class="match-button" :disabled="testLoading" @click="submitTest">
            提交并查看成绩与解析
          </button>
        </div>
        <p v-if="testError" class="error-text">{{ testError }}</p>
      </article>

      <article
        v-if="testSubmitted && testResult"
        class="result-card"
      >
        <h3 class="portal-section-title portal-section-title--emerald">成绩与解析</h3>
        <p><strong>总分：</strong>{{ testResult.totalScore }} / {{ testResult.fullScore }}</p>

        <div v-for="(q, idx) in testQuestions" :key="idx" class="ui-mt-14">
          <h4>第 {{ idx + 1 }} 题</h4>
          <p class="panel-subtitle">
            得分：{{ (testResult.perQuestionScores[idx]?.score) || 0 }} / 10
          </p>
          <div class="latex-block" v-html="renderLatexText(q.question)"></div>
          <p>
            <strong>你的答案：</strong><span v-html="renderLatexText(resolveAnswerText(q, testAnswers[idx]))"></span>
          </p>
          <p>
            <strong>正确答案：</strong><span v-html="renderLatexText(resolveAnswerText(q, q.answer))"></span>
          </p>

          <div class="ui-mt-10">
            <h4>解析</h4>
            <div class="latex-block" v-html="renderLatexText(q.explanation)"></div>
          </div>
        </div>

        <!-- 再来一套 -->
        <div class="inline-form ui-mt-16">
          <button type="button" class="match-button" :disabled="testLoading" @click="emit('regenerate')">
            {{ testLoading ? '生成中...' : '再来一套' }}
          </button>
        </div>
      </article>
    </template>
  </section>
</template>

<style scoped>
.test-count-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.test-count-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #475569;
}
.test-count-input {
  width: 100%;
  text-align: center;
}
.kp-multi-select {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.kp-multi-select-label {
  font-size: 13px;
  color: #475569;
  font-weight: 500;
}
.kp-multi-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.kp-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #eef2ff;
  color: #4338ca;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
}
.kp-tag-remove {
  background: none;
  border: none;
  color: #4338ca;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  padding: 0 2px;
}
.kp-add-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
@media (max-width: 600px) {
  .test-count-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

<style>
@import '@/student/styles/student-portal.css';
</style>
