<script setup>
import StudentWrongDrill from './StudentWrongDrill.vue'

const props = defineProps({
  filteredWrongBookForLearningPage: { type: Array, required: true },
  filteredLearningRecordsForLearningPage: { type: Array, required: true },
  savedExams: { type: Array, required: true },
  examError: { type: String, required: false, default: '' },

  wrongBookModalItem: { type: Object, default: null },
  wrongDrillCourse: { type: String, default: '' },
  wrongDrillCourseOptions: { type: Array, default: () => [] },
  wrongDrillSession: { type: Object, default: null },
  wrongDrillError: { type: String, default: '' },
  wrongDrillSubmitting: { type: Boolean, default: false },
  inferWrongBookQuestionType: { type: Function, required: true },
  setWrongDrillCourse: { type: Function, required: true },
  startWrongDrill: { type: Function, required: true },
  cancelWrongDrill: { type: Function, required: true },
  submitWrongDrill: { type: Function, required: true },
  renderLatexText: { type: Function, required: true },
  parseOptionLetter: { type: Function, required: true },
  parseOptionText: { type: Function, required: true },
  wrongBookQuestionPreview: { type: Function, required: true },

  openWrongBookModal: { type: Function, required: true },
  closeWrongBookModal: { type: Function, required: true },
  confirmDeleteExam: { type: Function, required: true },
  downloadExam: { type: Function, required: true },
  renderExamPdfs: { type: Function, required: true }
})

defineEmits(['go-courses'])

const wbQuestionType = (item) => String(item?.questionType || item?.question_type || '').trim()

/** 新结构：完整选项区 / 填空双行；旧数据仍走纯文本两行 */
const wrongBookRichLayout = (item) => {
  if (!item) return false
  const t = wbQuestionType(item)
  if (t === '填空题') return true
  if (['选择题', '多选题', '判断题'].includes(t) && Array.isArray(item.options) && item.options.length > 0) return true
  return false
}

const optionMarks = (opt, item) => {
  const L = props.parseOptionLetter(opt)
  if (!L) return { user: false, correct: false }
  const qt = wbQuestionType(item)
  const u = String(item.userOptionLetters || '').toUpperCase()
  const c = String(item.correctOptionLetters || '').toUpperCase()
  const userPick = qt === '多选题' ? u.includes(L) : u === L
  const correctPick = qt === '多选题' ? c.includes(L) : c === L
  return { user: userPick, correct: correctPick }
}
</script>

<template>
  <section class="panel-stack">
    <article v-if="!wrongDrillSession" class="result-card">
      <h3 class="portal-section-title portal-section-title--rose">错题巩固测试</h3>
      <div v-if="wrongDrillCourseOptions.length" class="inline-form wrong-drill-toolbar">
        <label class="wrong-drill-course-label">
          课程
          <select
            class="wrong-drill-select"
            :value="wrongDrillCourse"
            @change="setWrongDrillCourse($event.target.value)"
          >
            <option v-for="o in wrongDrillCourseOptions" :key="'wd-' + o.course" :value="o.course">
              {{ o.course }}（{{ o.count }} 题可测）
            </option>
          </select>
        </label>
        <button type="button" class="match-button" @click="startWrongDrill">开始随机测试</button>
      </div>
      <p v-if="wrongDrillError && !wrongDrillSession" class="error-text">{{ wrongDrillError }}</p>
    </article>

    <StudentWrongDrill
      v-if="wrongDrillSession"
      :session="wrongDrillSession"
      :submitting="wrongDrillSubmitting"
      :error="wrongDrillError"
      :render-latex-text="renderLatexText"
      :parse-option-letter="parseOptionLetter"
      :parse-option-text="parseOptionText"
      :infer-question-type="inferWrongBookQuestionType"
      @cancel="cancelWrongDrill"
      @submit="submitWrongDrill"
    />

    <article class="result-card">
      <h3 class="portal-section-title portal-section-title--violet">错题本</h3>
      <div v-if="(filteredWrongBookForLearningPage || []).length" class="wrong-book-grid">
        <article v-for="item in filteredWrongBookForLearningPage" :key="item.id" class="wrong-book-card">
          <div class="wrong-book-card-top">
            <strong class="wrong-book-title">{{ item.course }} · {{ item.knowledgePoint }}</strong>
            <div class="wrong-book-meta-line">
              <span>{{ item.score }} / {{ item.fullScore }} 分</span>
              <span class="wrong-book-time">{{ item.collectedAt }}</span>
            </div>
          </div>
          <p class="wrong-book-preview">{{ wrongBookQuestionPreview(item.question) }}</p>
          <div class="wrong-book-card-actions">
            <button type="button" class="match-button wrong-book-toggle" @click.stop="openWrongBookModal(item)">
              查看题目与解析
            </button>
          </div>
        </article>
      </div>
    </article>

    <article class="result-card">
      <h3 class="portal-section-title portal-section-title--teal">学习记录</h3>
      <table v-if="(filteredLearningRecordsForLearningPage || []).length" class="data-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>课程</th>
            <th>记录</th>
            <th>得分</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredLearningRecordsForLearningPage" :key="item.id">
            <td>{{ item.time }}</td>
            <td>{{ item.course }}</td>
            <td>{{ item.knowledgePoint }}</td>
            <td>{{ item.score }} / {{ item.fullScore }}</td>
          </tr>
        </tbody>
      </table>
    </article>

    <article class="result-card">
      <h3 class="portal-section-title portal-section-title--amber">已保存试卷</h3>
      <table v-if="(savedExams || []).length" class="data-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in savedExams" :key="e.id">
            <td>{{ e.title || ('试卷-' + e.id) }}</td>
            <td>{{ e.createdAt ? new Date(e.createdAt).toLocaleString() : '-' }}</td>
            <td>
              <div class="ui-toolbar-row">
                <button
                  type="button"
                  class="match-button"
                  :disabled="!e.mdPaper"
                  @click="downloadExam(e.id, 'md_paper')"
                  :title="e.mdPaper ? '下载原卷' : 'Markdown 未生成'"
                >
                  下载原卷
                </button>
                <button
                  v-if="!(e.mdPaper && e.mdAnswer)"
                  type="button"
                  class="match-button"
                  @click="renderExamPdfs(e.id)"
                  title="生成 Markdown 文件"
                >
                  生成 MD
                </button>
                <button
                  type="button"
                  class="match-button"
                  :disabled="!e.mdAnswer"
                  @click="downloadExam(e.id, 'md_answer')"
                  :title="e.mdAnswer ? '下载答案' : 'Markdown 未生成'"
                >
                  下载答案
                </button>
                <button type="button" class="cancel-button" @click="confirmDeleteExam(e.id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="examError" class="error-text" style="margin-top:8px">{{ examError }}</p>
    </article>

    <Teleport to="body">
      <div v-if="wrongBookModalItem" class="modal-mask" @click.self="closeWrongBookModal">
        <div class="modal-wrapper wrong-book-modal-wrap">
          <div class="modal-container wrong-book-modal-box">
            <button type="button" class="modal-close" @click="closeWrongBookModal" aria-label="关闭">×</button>
            <h3 class="portal-section-title portal-section-title--cyan">题目与解析</h3>
            <p class="panel-subtitle wrong-book-modal-sub">
              {{ wrongBookModalItem.course }} · {{ wrongBookModalItem.knowledgePoint }}
            </p>
            <div class="wrong-book-modal-body">
              <p
                v-if="wrongBookModalItem.wrongBookSource === 'teacherPublished'"
                class="wrong-book-src-hint"
              >
                来源：教师发布测试
              </p>
              <p
                v-else-if="wrongBookModalItem.wrongBookSource === 'studentGenerated'"
                class="wrong-book-src-hint"
              >
                来源：自主生成测试
              </p>
              <div class="latex-block wrong-book-detail-q" v-html="renderLatexText(wrongBookModalItem.question)"></div>

              <template v-if="wrongBookRichLayout(wrongBookModalItem)">
                <template v-if="wbQuestionType(wrongBookModalItem) === '填空题'">
                  <h4 class="wrong-book-block-title">作答</h4>
                  <div class="wrong-book-opt-list">
                    <div class="wrong-book-opt-row wrong-book-opt-row--fill wrong-book-opt--user">
                      <span class="wrong-book-opt-badge" aria-hidden="true">我</span>
                      <div
                        class="wrong-book-opt-body latex-block"
                        v-html="renderLatexText(wrongBookModalItem.myAnswer || '')"
                      ></div>
                    </div>
                    <div class="wrong-book-opt-row wrong-book-opt-row--fill wrong-book-opt--correct">
                      <span class="wrong-book-opt-badge" aria-hidden="true">参</span>
                      <div
                        class="wrong-book-opt-body latex-block"
                        v-html="renderLatexText(wrongBookModalItem.answer || '')"
                      ></div>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <h4 class="wrong-book-block-title">选项</h4>
                  <div class="wrong-book-opt-list">
                    <div
                      v-for="(opt, oi) in wrongBookModalItem.options || []"
                      :key="'wb-opt-' + wrongBookModalItem.id + '-' + oi"
                      class="wrong-book-opt-row"
                      :class="{
                        'wrong-book-opt--user': optionMarks(opt, wrongBookModalItem).user,
                        'wrong-book-opt--correct': optionMarks(opt, wrongBookModalItem).correct
                      }"
                    >
                      <span class="wrong-book-opt-letter">{{ parseOptionLetter(opt) }}.</span>
                      <div
                        class="wrong-book-opt-body latex-block"
                        v-html="renderLatexText(parseOptionText(opt))"
                      ></div>
                      <span
                        v-if="optionMarks(opt, wrongBookModalItem).user && optionMarks(opt, wrongBookModalItem).correct"
                        class="wrong-book-opt-tag wrong-book-opt-tag--both"
                      >所选 · 正确</span>
                      <span
                        v-else-if="optionMarks(opt, wrongBookModalItem).user"
                        class="wrong-book-opt-tag wrong-book-opt-tag--me"
                      >所选</span>
                      <span
                        v-else-if="optionMarks(opt, wrongBookModalItem).correct"
                        class="wrong-book-opt-tag wrong-book-opt-tag--ok"
                      >参考答案</span>
                    </div>
                  </div>
                </template>
              </template>
              <template v-else>
                <p class="wrong-book-detail-row">
                  <strong>我的答案：</strong>
                  <span v-html="renderLatexText(wrongBookModalItem.myAnswer)"></span>
                </p>
                <p class="wrong-book-detail-row">
                  <strong>参考答案：</strong>
                  <span v-html="renderLatexText(wrongBookModalItem.answer)"></span>
                </p>
              </template>

              <div v-if="wrongBookModalItem.explanation" class="wrong-book-detail-explain">
                <h4 class="wrong-book-explain-heading">解析</h4>
                <div class="latex-block" v-html="renderLatexText(wrongBookModalItem.explanation)"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style>
@import './student-portal.css';
</style>
