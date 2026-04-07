<script setup>
import { defineModel } from 'vue'

const teacherKpTestAnswers = defineModel('teacherKpTestAnswers', { type: Array, default: () => [] })

const props = defineProps({
  canStudyCurrentCourse: { type: Boolean, required: true },
  selectedKnowledgePoint: { type: String, default: '' },

  teacherKpTest: { type: Object, default: null },
  teacherKpTestLoading: { type: Boolean, default: false },
  teacherKpTestError: { type: String, default: '' },
  teacherKpTestSubmitted: { type: Boolean, default: false },
  teacherKpTestResult: { type: Object, default: null },
  teacherKpTestSubmitting: { type: Boolean, default: false },

  submitTeacherKpTest: { type: Function, required: true },
  onGoCourses: { type: Function, default: null },

  renderLatexText: { type: Function, required: true },
  parseOptionLetter: { type: Function, required: true },
  parseOptionText: { type: Function, required: true },
  resolveAnswerText: { type: Function, required: true }
})

const emit = defineEmits(['go-courses'])

const goCourses = () => {
  if (props.onGoCourses) props.onGoCourses()
  else emit('go-courses')
}
</script>

<template>
  <section class="panel-stack">
    <article v-if="!canStudyCurrentCourse" class="result-card">
      <h3>暂不可学习</h3>
      <p class="panel-subtitle">
        请先在「课程广场」对某门已加入的课程点击「进入课程」，再进行教师测试（与个人中心「统计课程」无关）。
      </p>
      <button type="button" class="match-button" @click="goCourses">去课程广场</button>
    </article>

    <template v-else>
      <article class="result-card">
        <h3>教师发布测试</h3>
        <p class="panel-subtitle ui-mt-6">
          当前知识点：<strong>{{ selectedKnowledgePoint || '-' }}</strong>
        </p>
        <p v-if="teacherKpTestLoading" class="panel-subtitle">加载中…</p>
        <p v-else-if="teacherKpTestError" class="error-text">{{ teacherKpTestError }}</p>

        <template v-else-if="teacherKpTest && teacherKpTest.questions?.length">
          <p class="panel-subtitle" style="margin-top: 6px">
            题型为单选题与填空题。
          </p>

          <template v-if="!teacherKpTestSubmitted">
            <h3 style="margin-top: 10px">{{ teacherKpTest.title }}</h3>
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
                <div class="latex-block" v-html="renderLatexText(teacherKpTestResult.perQuestion[idx].explanation)"></div>
              </div>
            </div>
          </template>
        </template>

        <p v-else class="panel-subtitle ui-mt-12">该知识点暂无教师发布测试。</p>
      </article>
    </template>
  </section>
</template>

<style src="./student-portal.css"></style>

