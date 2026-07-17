import { computed, ref, watch } from 'vue'
import { fetchGrading } from '../../api/client'
import { gradeStudentAnswer } from '../utils/studentTestAnswerUtils'
import {
  inferWrongBookQuestionType,
  pickWrongDrillItemsFromBook,
  wrongBookItemToGradeQuestion
} from '../utils/studentWrongDrillUtils'

/**
 * 间隔重复复习间隔（天）：连续答对次数 -> 下次复习间隔
 */
const SPACED_REPETITION_INTERVALS = [1, 3, 7, 14, 30]

/**
 * 错题巩固测试：选题、提交、连续答对计数与学习记录写入
 * 支持间隔重复（spaced repetition）策略
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

  /** 计算下次复习日期（ISO 字符串） */
  const computeNextReviewAt = (consecutiveCorrect) => {
    const idx = Math.min(consecutiveCorrect, SPACED_REPETITION_INTERVALS.length - 1)
    const days = SPACED_REPETITION_INTERVALS[idx]
    const d = new Date()
    d.setDate(d.getDate() + days)
    return d.toISOString().split('T')[0] // YYYY-MM-DD
  }

  /** 待复习错题数（按课程分组） */
  const dueReviewCountByCourse = computed(() => {
    const today = new Date().toISOString().split('T')[0] // YYYY-MM-DD
    const m = new Map()
    for (const w of wrongBook.value || []) {
      // 已连续答对 2 次以上且未到复习时间的跳过
      const consecutive = Number(w.wrongTestConsecutiveCorrect || 0)
      if (consecutive >= 2) {
        const nextReview = String(w.nextReviewAt || '').trim()
        if (nextReview && nextReview > today) continue
      }
      const c = String(w.course || '').trim()
      if (!c) continue
      m.set(c, (m.get(c) || 0) + 1)
    }
    return m
  })

  /** 总待复习错题数（首页展示） */
  const totalDueReviewCount = computed(() => {
    let total = 0
    for (const count of dueReviewCountByCourse.value.values()) {
      total += count
    }
    return total
  })

  const wrongDrillEligibleByCourse = computed(() => {
    const m = new Map()
    for (const w of wrongBook.value || []) {
      const consecutive = Number(w.wrongTestConsecutiveCorrect || 0)
      if (consecutive >= 2) {
        const nextReview = String(w.nextReviewAt || '').trim()
        const today = new Date().toISOString().split('T')[0]
        if (nextReview && nextReview > today) continue
      }
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
          const newConsecutive = correct ? prev + 1 : 0
          wb[wi].wrongTestConsecutiveCorrect = newConsecutive
          // 间隔重复：设置下次复习日期
          wb[wi].nextReviewAt = correct ? computeNextReviewAt(newConsecutive) : ''
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
    dueReviewCountByCourse,
    totalDueReviewCount,
    inferWrongBookQuestionType,
    setWrongDrillCourse,
    startWrongDrill,
    cancelWrongDrill,
    submitWrongDrill
  }
}
