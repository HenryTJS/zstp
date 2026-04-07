<script setup>
defineProps({
  filteredWrongBookForLearningPage: { type: Array, required: true },
  filteredLearningRecordsForLearningPage: { type: Array, required: true },
  savedExams: { type: Array, required: true },
  examError: { type: String, required: false, default: '' },

  wrongBookModalItem: { type: Object, default: null },
  renderLatexText: { type: Function, required: true },
  wrongBookQuestionPreview: { type: Function, required: true },

  openWrongBookModal: { type: Function, required: true },
  closeWrongBookModal: { type: Function, required: true },
  removeWrongItem: { type: Function, required: true },
  removeLearningRecord: { type: Function, required: true },
  confirmDeleteExam: { type: Function, required: true },
  downloadExam: { type: Function, required: true },
  renderExamPdfs: { type: Function, required: true }
})

defineEmits(['go-courses'])
</script>

<template>
  <section class="panel-stack">
    <article class="result-card">
      <h3>错题本</h3>
      <p v-if="!(filteredWrongBookForLearningPage || []).length" class="panel-subtitle">暂无收藏错题。</p>
      <div v-else class="wrong-book-grid">
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
            <button type="button" class="cancel-button" @click.stop="removeWrongItem(item.id)">删除</button>
          </div>
        </article>
      </div>
    </article>

    <article class="result-card">
      <h3>学习记录</h3>
      <p v-if="!(filteredLearningRecordsForLearningPage || []).length" class="panel-subtitle">暂无学习记录。</p>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>课程</th>
            <th>知识点</th>
            <th>得分</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredLearningRecordsForLearningPage" :key="item.id">
            <td>{{ item.time }}</td>
            <td>{{ item.course }}</td>
            <td>{{ item.knowledgePoint }}</td>
            <td>{{ item.score }} / {{ item.fullScore }}</td>
            <td>
              <button type="button" class="cancel-button" @click="removeLearningRecord(item.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </article>

    <article class="result-card">
      <h3>已保存试卷</h3>
      <p v-if="!(savedExams || []).length" class="panel-subtitle">暂无已保存试卷。</p>
      <table v-else class="data-table">
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
                  :title="e.mdPaper ? '下载原卷 (Markdown)' : 'Markdown 未生成'"
                >
                  下载 MD
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
                  :title="e.mdAnswer ? '下载答案 (Markdown)' : 'Markdown 未生成'"
                >
                  下载 答案 (MD)
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
            <h3>题目与解析</h3>
            <p class="panel-subtitle wrong-book-modal-sub">
              {{ wrongBookModalItem.course }} · {{ wrongBookModalItem.knowledgePoint }}
            </p>
            <div class="wrong-book-modal-body">
              <div class="latex-block wrong-book-detail-q" v-html="renderLatexText(wrongBookModalItem.question)"></div>
              <p class="wrong-book-detail-row">
                <strong>我的答案：</strong><span v-html="renderLatexText(wrongBookModalItem.myAnswer)"></span>
              </p>
              <p class="wrong-book-detail-row">
                <strong>参考答案：</strong><span v-html="renderLatexText(wrongBookModalItem.answer)"></span>
              </p>
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

<style src="./student-portal.css"></style>
