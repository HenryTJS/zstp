<script setup>

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

const emit = defineEmits(['go-courses'])

const goCourses = () => {
  // 优先用 emit（推荐），兼容旧用法给 onGoCourses
  if (props.onGoCourses) props.onGoCourses()
  else emit('go-courses')
}
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

          <div class="inline-form ui-mt-12">
            <button type="button" class="match-button" :disabled="testLoading" @click="generateTest">
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
      </article>
    </template>
  </section>
</template>

<style>
@import './student-portal.css';
</style>
