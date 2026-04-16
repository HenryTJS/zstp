import { ref } from 'vue'
import { deleteExam, fetchExams, http } from '../../../api/client'

export function useStudentReviewModule() {
  const examError = ref('')
  const savedExams = ref([])
  const wrongBookModalItem = ref(null)

  const loadSavedExams = async () => {
    try {
      const { data } = await fetchExams()
      savedExams.value = Array.isArray(data) ? data : []
    } catch {
      savedExams.value = []
    }
  }

  const openWrongBookModal = (item) => {
    wrongBookModalItem.value = item || null
  }

  const closeWrongBookModal = () => {
    wrongBookModalItem.value = null
  }

  const downloadExam = (id, type) => {
    if (!id) return
    window.open('/api/exams/' + id + '/download?type=' + type)
  }

  const renderExamPdfs = async (id) => {
    if (!id) return
    try {
      const { data } = await http.post('/exams/' + id + '/render')
      await loadSavedExams()
      if (data && data.mdPaper) downloadExam(id, 'md_paper')
      if (data && data.mdAnswer) downloadExam(id, 'md_answer')
    } catch (e) {
      console.error('renderExamPdfs failed', e)
      examError.value = '服务器生成 Markdown 文件失败，请稍后重试。'
    }
  }

  const confirmDeleteExam = async (id) => {
    if (!id) return
    if (!confirm('确定要删除该已保存试卷吗？此操作不可恢复。')) return
    try {
      await deleteExam(id)
      await loadSavedExams()
    } catch {
      examError.value = '删除失败，请稍后重试。'
    }
  }

  return {
    examError,
    savedExams,
    wrongBookModalItem,
    loadSavedExams,
    openWrongBookModal,
    closeWrongBookModal,
    downloadExam,
    renderExamPdfs,
    confirmDeleteExam
  }
}

