<script setup>
import { defineModel } from 'vue'

const teacherKpTestAnswers = defineModel('teacherKpTestAnswers', { type: Array, default: () => [] })

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

  teacherKpTest: { type: Object, default: null },
  teacherKpTestLoading: { type: Boolean, default: false },
  teacherKpTestError: { type: String, default: '' },
  teacherKpTestSubmitted: { type: Boolean, default: false },
  teacherKpTestResult: { type: Object, default: null },
  teacherKpTestSubmitting: { type: Boolean, default: false },

  // actions
  generateTest: { type: Function, required: true },
  submitTest: { type: Function, required: true },
  submitTeacherKpTest: { type: Function, required: true },
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
      <article
        v-if="teacherKpTest && teacherKpTest.questions?.length"
        class="result-card"
      >
        <h3>教师发布测试 · {{ teacherKpTest.title }}</h3>
        <p class="panel-subtitle" style="margin-top: 6px">
          题型为单选题与填空题；成绩与下方「自主 AI 测试」一并计入学习记录与掌握度。
        </p>
        <p v-if="teacherKpTestLoading" class="panel-subtitle">加载中…</p>
        <p v-else-if="teacherKpTestError" class="error-text">{{ teacherKpTestError }}</p>

        <template v-else-if="!teacherKpTestSubmitted">
          <div v-for="(q, idx) in teacherKpTest.questions" :key="'tk-' + idx" class="ui-mt-14">
            <h4>第 {{ idx + 1 }} 题（{{ q.question_type }} · {{ q.fullScore ?? 5 }} 分）</h4>
            <p v-if="q.focusPointName" class="panel-subtitle">考查知识点：{{ q.focusPointName }}</p>
            <div class="latex-block" v-html="renderLatexText(q.question)"></div>

            <div v-if="q.question_type === '选择题'" class="option-list">
              <label v-for="opt in q.options || []" :key="opt" class="option-item">
                <input
                  type="radio"
                  :name="'tk-q-' + idx"
                  :value="parseOptionLetter(opt)"
                  v-model="teacherKpTestAnswers[idx]"
                />
                <span v-html="renderLatexText(parseOptionText(opt))"></span>
              </label>
            </div>

            <div v-else-if="q.question_type === '填空题'">
              <label>
                作答
                <input class="match-height" v-model="teacherKpTestAnswers[idx]" placeholder="请输入答案" />
              </label>
            </div>
          </div>

          <div class="inline-form ui-mt-12">
            <button type="button" class="match-button" :disabled="teacherKpTestSubmitting" @click="submitTeacherKpTest">
              {{ teacherKpTestSubmitting ? '提交中…' : '提交教师测试' }}
            </button>
          </div>
        </template>

        <template v-else-if="teacherKpTestSubmitted && teacherKpTestResult">
          <p>
            <strong>总分：</strong>{{ teacherKpTestResult.totalScore }} / {{ teacherKpTestResult.fullScore }}
          </p>
          <div v-for="(q, idx) in teacherKpTest.questions" :key="'tkr-' + idx" class="ui-mt-14">
            <h4>第 {{ idx + 1 }} 题</h4>
            <p v-if="q.focusPointName" class="panel-subtitle">考查知识点：{{ q.focusPointName }}</p>
            <p class="panel-subtitle">
              得分：{{ teacherKpTestResult.perQuestion?.[idx]?.score ?? 0 }} /
              {{ teacherKpTestResult.perQuestion?.[idx]?.full_score ?? q.fullScore ?? 5 }}
            </p>
            <div class="latex-block" v-html="renderLatexText(q.question)"></div>
            <p>
              <strong>你的答案：</strong>
              <span v-html="renderLatexText(resolveAnswerText(q, teacherKpTestAnswers[idx]))"></span>
            </p>
            <div v-if="teacherKpTestResult.perQuestion?.[idx]?.explanation" style="margin-top: 8px">
              <h4>解析</h4>
              <div
                class="latex-block"
                v-html="renderLatexText(teacherKpTestResult.perQuestion[idx].explanation)"
              ></div>
            </div>
          </div>
        </template>
      </article>

      <article class="result-card">
        <h3>出题与做题</h3>
        <p class="panel-subtitle ui-mt-6">
          当前知识点：<strong>{{ selectedKnowledgePoint || '-' }}</strong>
        </p>

        <p v-if="!selectedKnowledgePoint" class="panel-subtitle ui-mt-12">
          请先回到「知识图谱」点击一个知识点，再在此进行测试。
        </p>

        <div v-else>
          <div class="grid-form two-col ui-mt-12">
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
              <div class="panel-subtitle ui-mt-6">测试：固定 5 题（单选）</div>
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
        <h3>测试题目</h3>
        <p class="panel-subtitle">作答完成后点击“提交并查看成绩与解析”。</p>

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
        <h3>成绩与解析</h3>
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

<style src="./student-portal.css"></style>
