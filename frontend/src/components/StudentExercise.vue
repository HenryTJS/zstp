<script setup>
const props = defineProps({
  canStudyCurrentCourse: { type: Boolean, required: true },
  selectedKnowledgePoint: { type: String, default: '' },
  questionForm: { type: Object, required: true },
  examMode: { type: String, required: true },
  practiceBatchAllowed: { type: Boolean, required: true },
  practiceTestAllowed: { type: Boolean, required: true },

  // test
  testLoading: { type: Boolean, required: true },
  testError: { type: String, required: true },
  testQuestions: { type: Array, required: true },
  testSubmitted: { type: Boolean, required: true },
  testResult: { type: Object, default: null },
  testAnswers: { type: Array, required: true },

  // exam/batch
  examLoading: { type: Boolean, required: true },
  examError: { type: String, required: true },
  examResult: { type: Array, default: () => [] },

  // actions
  generateTest: { type: Function, required: true },
  submitTest: { type: Function, required: true },
  generateExam: { type: Function, required: true },
  persistGeneratedExam: { type: Function, required: true },
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
      <h3>暂不可学习</h3>
      <p class="panel-subtitle">
        请先在「课程广场」对某门已加入的课程点击「进入课程」，再在此出题与做题（与个人中心「统计课程」无关）。
      </p>
      <button type="button" class="match-button" @click="goCourses">去课程广场</button>
    </article>

    <template v-else>
      <article class="result-card">
        <h3>出题与做题</h3>
        <p class="panel-subtitle" style="margin-top:6px">
          当前知识点：<strong>{{ selectedKnowledgePoint || '-' }}</strong>
        </p>

        <p v-if="!selectedKnowledgePoint" class="panel-subtitle" style="margin-top:12px">
          请先回到「知识图谱」点击一个知识点，然后选择进入测试/组卷。
        </p>

        <div v-else>
          <div class="grid-form two-col" style="margin-top:12px">
            <label>
              难度
              <select v-model="questionForm.difficulty" class="match-height">
                <option>基础</option>
                <option>中等</option>
                <option>拔高</option>
              </select>
            </label>
            <label>
              规则
              <div class="panel-subtitle" style="margin-top:6px">
                <template v-if="examMode === 'single'">测试：固定 5 题（单选）</template>
                <template v-else>组卷：固定 10 题（3单选 + 3填空 + 4解答）</template>
              </div>
            </label>
          </div>

          <div v-if="examMode === 'single'" class="inline-form" style="margin-top:12px;">
            <button :disabled="testLoading" @click="generateTest">
              {{ testLoading ? '生成中...' : '开始测试' }}
            </button>
          </div>

          <p v-if="testError && !(testQuestions.length && !testSubmitted)" class="error-text">{{ testError }}</p>

          <div v-if="examMode === 'batch'" class="inline-form" style="margin-top:12px;">
            <button :disabled="examLoading || !practiceBatchAllowed" @click="generateExam">
              {{ examLoading ? '生成中...' : '生成试卷' }}
            </button>
          </div>

          <p
            v-if="examMode === 'batch' && !practiceBatchAllowed"
            class="panel-subtitle"
            style="margin-top:10px"
          >
            组卷仅支持「课程名」和「章节名」。请回到知识图谱重新选择。
          </p>

          <p v-if="examError" class="error-text" style="margin-top:8px">{{ examError }}</p>

          <div
            v-if="examMode === 'batch' && examResult && examResult.length"
            class="inline-form"
            style="margin-top:8px"
          >
            <button :disabled="examLoading" @click="persistGeneratedExam">保存试卷并生成 MD</button>
          </div>
        </div>
      </article>

      <article
        v-if="examMode === 'single' && testQuestions.length && !testSubmitted"
        class="result-card"
      >
        <h3>测试题目</h3>
        <p class="panel-subtitle">作答完成后点击“提交并查看成绩与解析”。</p>

        <div v-for="(q, idx) in testQuestions" :key="idx" style="margin-top:14px;">
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

        <div class="inline-form" style="margin-top:12px;">
          <button :disabled="testLoading" @click="submitTest">提交并查看成绩与解析</button>
        </div>
        <p v-if="testError" class="error-text">{{ testError }}</p>
      </article>

      <article
        v-if="examMode === 'single' && testSubmitted && testResult"
        class="result-card"
      >
        <h3>成绩与解析</h3>
        <p><strong>总分：</strong>{{ testResult.totalScore }} / {{ testResult.fullScore }}</p>

        <div v-for="(q, idx) in testQuestions" :key="idx" style="margin-top:14px;">
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

          <div style="margin-top:10px;">
            <h4>解析</h4>
            <div class="latex-block" v-html="renderLatexText(q.explanation)"></div>
          </div>
        </div>
      </article>
    </template>
  </section>
</template>

<style src="./student-portal.css"></style>
