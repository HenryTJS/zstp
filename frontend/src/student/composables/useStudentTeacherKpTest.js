import { ref, watch } from 'vue'
import {
  getKnowledgePointPublishedTestForStudent,
  getMyKnowledgePointPublishedTestSubmission,
  submitKnowledgePointPublishedTest
} from '../../api/client'
import { resolveAnswerText, unescapeNewlinesSafe, wrongBookChoiceLetters } from '../utils/studentTestAnswerUtils'

/**
 * 教师发布知识点测试：加载、提交、图谱页 meta 概览
 */
export function useStudentTeacherKpTest({
  currentPage,
  effectivePage,
  canStudyCurrentCourse,
  learningContextCourse,
  selectedKnowledgePoint,
  getUserId,
  learningRecords,
  wrongBook,
  selectedMajorDisplay,
  selectedMajor,
  schedulePersistStudentState,
  markCompletedSafe,
  router
}) {
  const teacherKpTest = ref(null)
  const teacherKpTestLoading = ref(false)
  const teacherKpTestError = ref('')
  const teacherKpTestAnswers = ref([])
  const teacherKpTestSubmitted = ref(false)
  const teacherKpTestResult = ref(null)
  const teacherKpTestSubmitting = ref(false)

  const teacherKpTestMeta = ref(null)
  const teacherKpTestMetaLoading = ref(false)
  const teacherKpTestMetaError = ref('')

  const loadTeacherKpTest = async () => {
    teacherKpTest.value = null
    teacherKpTestError.value = ''
    teacherKpTestSubmitted.value = false
    teacherKpTestResult.value = null
    teacherKpTestAnswers.value = []
    const uid = getUserId()
    if (!canStudyCurrentCourse.value || !learningContextCourse.value || !selectedKnowledgePoint.value || !uid) {
      return
    }
    teacherKpTestLoading.value = true
    try {
      const { data } = await getKnowledgePointPublishedTestForStudent({
        courseName: learningContextCourse.value,
        pointName: selectedKnowledgePoint.value,
        userId: uid
      })
      const t = data?.test
      if (t && Array.isArray(t.questions) && t.questions.length) {
        teacherKpTest.value = t
        teacherKpTestAnswers.value = t.questions.map(() => '')

        try {
          const { data: sub } = await getMyKnowledgePointPublishedTestSubmission({
            userId: uid,
            testId: t.id
          })
          if (sub?.submitted) {
            teacherKpTestResult.value = sub
            teacherKpTestSubmitted.value = true
            const per = Array.isArray(sub?.perQuestion) ? sub.perQuestion : []
            if (per.length) {
              teacherKpTestAnswers.value = per.map((r) => String(r?.studentAnswer ?? '').trim())
            }
            await markCompletedSafe(`TEST:${t.id}`)
          }
        } catch {
          /* ignore */
        }
      } else {
        teacherKpTest.value = null
      }
    } catch (e) {
      teacherKpTestError.value = e?.response?.data?.message || ''
      teacherKpTest.value = null
    } finally {
      teacherKpTestLoading.value = false
    }
  }

  const loadTeacherKpTestMeta = async () => {
    teacherKpTestMeta.value = null
    teacherKpTestMetaError.value = ''
    const uid = getUserId()
    if (!canStudyCurrentCourse.value || !learningContextCourse.value || !selectedKnowledgePoint.value || !uid) {
      return
    }
    teacherKpTestMetaLoading.value = true
    try {
      const { data } = await getKnowledgePointPublishedTestForStudent({
        courseName: learningContextCourse.value,
        pointName: selectedKnowledgePoint.value,
        userId: uid
      })
      const t = data?.test
      const qs = Array.isArray(t?.questions) ? t.questions : []
      if (t?.id && qs.length) {
        teacherKpTestMeta.value = {
          id: t.id,
          title: t.title || '',
          updatedAt: t.updatedAt || null,
          questionCount: qs.length
        }
      } else {
        teacherKpTestMeta.value = null
      }
    } catch (e) {
      teacherKpTestMetaError.value = e?.response?.data?.message || ''
      teacherKpTestMeta.value = null
    } finally {
      teacherKpTestMetaLoading.value = false
    }
  }

  watch(
    () => [currentPage.value, learningContextCourse.value, selectedKnowledgePoint.value, getUserId()],
    () => {
      if (currentPage.value !== 'teacher-test') {
        teacherKpTest.value = null
        teacherKpTestSubmitted.value = false
        teacherKpTestResult.value = null
        teacherKpTestAnswers.value = []
        teacherKpTestError.value = ''
        return
      }
      void loadTeacherKpTest()
    }
  )

  watch(
    () => [effectivePage.value, learningContextCourse.value, selectedKnowledgePoint.value, getUserId()],
    () => {
      if (effectivePage.value !== 'graph') {
        teacherKpTestMeta.value = null
        teacherKpTestMetaError.value = ''
        teacherKpTestMetaLoading.value = false
        return
      }
      void loadTeacherKpTestMeta()
    }
  )

  const submitTeacherKpTest = async () => {
    teacherKpTestError.value = ''
    const uid = getUserId()
    if (!teacherKpTest.value?.id || !uid) return
    const qs = teacherKpTest.value.questions || []
    if (!Array.isArray(teacherKpTestAnswers.value) || teacherKpTestAnswers.value.length !== qs.length) {
      teacherKpTestError.value = '作答数据异常。'
      return
    }
    for (let i = 0; i < qs.length; i++) {
      if (!String(teacherKpTestAnswers.value[i] ?? '').trim()) {
        teacherKpTestError.value = `请完成第 ${i + 1} 题。`
        return
      }
    }
    teacherKpTestSubmitting.value = true
    try {
      const { data } = await submitKnowledgePointPublishedTest({
        userId: uid,
        testId: teacherKpTest.value.id,
        answers: teacherKpTestAnswers.value.map((a) => String(a ?? '').trim())
      })
      teacherKpTestResult.value = data
      teacherKpTestSubmitted.value = true

      const now = new Date()
      const nowDisplay = now.toLocaleString()
      const nowIso = now.toISOString()
      const per = Array.isArray(data?.perQuestion) ? data.perQuestion : []
      const kpAnchor = String(selectedKnowledgePoint.value || '').trim()
      const newLR = []
      const newWB = []
      const totalTeacherScore = Number(data?.totalScore ?? 0)
      const totalTeacherFull = Number(data?.fullScore ?? 0)
      const nTeacherQ = per.length
      newLR.push({
        id: `lr-tt-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
        time: nowDisplay,
        major: selectedMajorDisplay.value || selectedMajor.value || '',
        course: learningContextCourse.value || '',
        knowledgePoint: `教师发布测试 · ${kpAnchor || '未标注'}${nTeacherQ ? `（${nTeacherQ} 题）` : ''}`,
        practiceAnchorLabel: kpAnchor || '',
        score: totalTeacherScore,
        fullScore: totalTeacherFull,
        questionCount: nTeacherQ
      })
      for (let i = 0; i < per.length; i++) {
        const row = per[i]
        const score = Number(row.score ?? 0)
        const full = Number(row.full_score ?? 0)
        const kpRow =
          String(qs[i]?.focusPointName || '').trim() ||
          String(row.focusPointName || '').trim() ||
          kpAnchor ||
          '未标注'
        if (score < full) {
          const q = qs[i] || {}
          const safeOptions = Array.isArray(q.options) ? q.options.map(unescapeNewlinesSafe) : []
          newWB.push({
            id: `wb-tt-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 7)}`,
            wrongBookSource: 'teacherPublished',
            questionType: q.question_type || String(row.question_type || ''),
            options: safeOptions,
            studentAnswerRaw: teacherKpTestAnswers.value[i],
            referenceAnswerRaw: q.answer,
            userOptionLetters: wrongBookChoiceLetters(q, teacherKpTestAnswers.value[i]),
            correctOptionLetters: wrongBookChoiceLetters(q, q.answer),
            major: selectedMajorDisplay.value || selectedMajor.value || '',
            course: learningContextCourse.value || '',
            knowledgePoint: kpRow,
            question: String(row.question || q.question || ''),
            explanation: String(row.explanation || ''),
            myAnswer: resolveAnswerText(q, teacherKpTestAnswers.value[i]),
            answer: resolveAnswerText(q, q.answer),
            score,
            fullScore: full,
            collectedAt: nowDisplay,
            collectedAtIso: nowIso
          })
        }
      }
      learningRecords.value = [...newLR, ...(learningRecords.value || [])]
      wrongBook.value = [...newWB, ...(wrongBook.value || [])]
      schedulePersistStudentState()

      await markCompletedSafe(`TEST:${teacherKpTest.value.id}`)
    } catch (e) {
      const status = e?.response?.status
      const payload = e?.response?.data
      if (status === 409 && payload && typeof payload === 'object' && payload.alreadySubmitted) {
        teacherKpTestResult.value = payload
        teacherKpTestSubmitted.value = true
        await markCompletedSafe(`TEST:${teacherKpTest.value.id}`)
        teacherKpTestError.value = ''
      } else {
        teacherKpTestError.value = e?.response?.data?.message || '提交失败'
      }
    } finally {
      teacherKpTestSubmitting.value = false
    }
  }

  const enterTeacherKpTestFromGraph = async () => {
    if (!selectedKnowledgePoint.value) return
    currentPage.value = 'teacher-test'
    try {
      await router.push('/student/teacher-test')
    } catch {
      /* ignore */
    }
  }

  return {
    teacherKpTest,
    teacherKpTestLoading,
    teacherKpTestError,
    teacherKpTestAnswers,
    teacherKpTestSubmitted,
    teacherKpTestResult,
    teacherKpTestSubmitting,
    teacherKpTestMeta,
    teacherKpTestMetaLoading,
    teacherKpTestMetaError,
    submitTeacherKpTest,
    enterTeacherKpTestFromGraph
  }
}
