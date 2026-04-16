import { computed, ref, watch } from 'vue'
import { fetchGrading } from '../../api/client'
import { gradeStudentAnswer } from '../utils/studentTestAnswerUtils'
import {
  inferWrongBookQuestionType,
  pickWrongDrillItemsFromBook,
  wrongBookItemToGradeQuestion
} from '../utils/studentWrongDrillUtils'

/**
 * 错题巩固测试：选题、提交、连续答对计数与学习记录写入
 */
export function useStudentWrongDrill({
  wrongBook,
  learningRecords,
  joinedCourses,
  selectedMajorDisplay,
  selectedMajor,
  effectivePage,
  schedulePersistStudentState
}) {
  const wrongDrillCourse = ref('')
  const wrongDrillSession = ref(null)
  const wrongDrillError = ref('')
  const wrongDrillSubmitting = ref(false)

  const wrongDrillEligibleByCourse = computed(() => {
    const m = new Map()
    for (const w of wrongBook.value || []) {
      if (Number(w.wrongTestConsecutiveCorrect || 0) >= 2) continue
      const c = String(w.course || '').trim()
      if (!c) continue
      m.set(c, (m.get(c) || 0) + 1)
    }
    return m
  })

  const wrongDrillCourseOptions = computed(() => {
    const jc = joinedCourses.value || []
    const m = wrongDrillEligibleByCourse.value
    return jc
      .map((course) => ({ course, count: m.get(course) || 0 }))
      .filter((x) => x.count > 0)
  })

  watch(
    wrongDrillCourseOptions,
    (opts) => {
      if (!opts.length) return
      const cur = String(wrongDrillCourse.value || '').trim()
      if (!cur || !opts.some((o) => o.course === cur)) {
        wrongDrillCourse.value = opts[0].course
      }
    },
    { immediate: true }
  )

  watch(
    () => effectivePage.value,
    (p) => {
      if (p !== 'review' && wrongDrillSession.value) {
        wrongDrillSession.value = null
        wrongDrillError.value = ''
      }
    }
  )

  const startWrongDrill = () => {
    wrongDrillError.value = ''
    const course = String(wrongDrillCourse.value || '').trim()
    if (!course) {
      wrongDrillError.value = '请选择课程。'
      return
    }
    const items = pickWrongDrillItemsFromBook(course, wrongBook.value)
    if (!items.length) {
      wrongDrillError.value =
        '该课程暂无可用错题（已全部在错题巩固中连续答对两次退出，或错题本为空）。'
      return
    }
    wrongDrillSession.value = { course, items }
  }

  const cancelWrongDrill = () => {
    wrongDrillSession.value = null
    wrongDrillError.value = ''
  }

  const setWrongDrillCourse = (v) => {
    wrongDrillCourse.value = typeof v === 'string' ? v : String(v ?? '')
  }

  const submitWrongDrill = async (answerRows) => {
    wrongDrillError.value = ''
    const sess = wrongDrillSession.value
    if (!sess?.items?.length) {
      wrongDrillError.value = '会话已失效，请重新开始。'
      return
    }
    if (!Array.isArray(answerRows) || answerRows.length !== sess.items.length) {
      wrongDrillError.value = '作答数据异常，请重新开始。'
      return
    }
    wrongDrillSubmitting.value = true
    try {
      const now = new Date()
      const nowDisplay = now.toLocaleString()
      const graders = sess.items.map((item, idx) => {
        const q = wrongBookItemToGradeQuestion(item)
        const studentAnswer = gradeStudentAnswer(q, answerRows[idx])
        const fullScore = Math.max(1, Math.min(100, Number(item.fullScore) || 10))
        return fetchGrading({
          question: q.question,
          referenceAnswer: q.answer,
          studentAnswer,
          questionType: q.question_type,
          studentAnswerImageBase64: '',
          studentAnswerImageName: '',
          fullScore
        })
      })
      const results = await Promise.all(graders)
      const perQuestionScores = results.map((r) => (r && r.data ? r.data : r)).map((d) => d || {})

      const wb = [...(wrongBook.value || [])]
      const newLR = []
      for (let i = 0; i < sess.items.length; i++) {
        const item = sess.items[i]
        const result = perQuestionScores[i] || {}
        const score = Number(result.score || 0)
        const full = Math.max(1, Math.min(100, Number(item.fullScore) || 10))
        const correct = score >= full

        const wi = wb.findIndex((w) => w.id === item.id)
        if (wi >= 0) {
          const prev = Number(wb[wi].wrongTestConsecutiveCorrect || 0)
          wb[wi].wrongTestConsecutiveCorrect = correct ? prev + 1 : 0
        }

        const kp = String(item.knowledgePoint || '').trim() || '未标注'
        newLR.push({
          id: `lr-wd-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 9)}`,
          time: nowDisplay,
          major:
            String(item.major || '').trim() ||
            selectedMajorDisplay.value ||
            selectedMajor.value ||
            '',
          course: String(item.course || sess.course || '').trim(),
          knowledgePoint: `错题巩固 · ${kp}`,
          practiceAnchorLabel: kp,
          score,
          fullScore: full,
          questionCount: 1
        })
      }

      wrongBook.value = wb
      learningRecords.value = [...newLR, ...(learningRecords.value || [])]
      wrongDrillSession.value = null
      schedulePersistStudentState()
    } catch (e) {
      wrongDrillError.value =
        e?.response?.data?.message || e?.message || '提交失败，请稍后重试。'
    } finally {
      wrongDrillSubmitting.value = false
    }
  }

  return {
    wrongDrillCourse,
    wrongDrillSession,
    wrongDrillError,
    wrongDrillSubmitting,
    wrongDrillCourseOptions,
    inferWrongBookQuestionType,
    setWrongDrillCourse,
    startWrongDrill,
    cancelWrongDrill,
    submitWrongDrill
  }
}
