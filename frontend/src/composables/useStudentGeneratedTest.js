import { computed } from 'vue'
import { fetchGrading, fetchQuestions } from '../api/client'
import {
  gradeStudentAnswer,
  resolveAnswerText,
  unescapeNewlinesSafe,
  wrongBookChoiceLetters
} from '../utils/studentTestAnswerUtils'
import { pickRandomSubpointLabelForKnowledgePoint } from '../utils/studentGraphTraversal'

/**
 * 学生自主组卷生成与提交（客观题多题）
 */
export function useStudentGeneratedTest({
  graphData,
  questionForm,
  testForm,
  testCounts,
  testQuestions,
  testAnswers,
  testLoading,
  testError,
  testSubmitted,
  testResult,
  learningContextCourse,
  selectedKnowledgePoint,
  learningRecords,
  wrongBook,
  selectedMajorDisplay,
  selectedMajor,
  composeTopic,
  schedulePersistStudentState
}) {
  const testTotalCount = computed(
    () =>
      Number(testCounts.value.singleChoiceCount || 0) +
      Number(testCounts.value.multiChoiceCount || 0) +
      Number(testCounts.value.judgeCount || 0) +
      Number(testCounts.value.fillCount || 0)
  )

  const resetTestState = () => {
    testQuestions.value = []
    testAnswers.value = []
    testSubmitted.value = false
    testResult.value = null
    testError.value = ''
  }

  const generateTest = async () => {
    resetTestState()
    testError.value = ''
    testLoading.value = true
    try {
      const topics = Array.isArray(testForm.value.selectedPoints) ? testForm.value.selectedPoints : []
      if (!topics.length) {
        testError.value = '请选择一个或多个知识点。'
        return
      }
      const total = testTotalCount.value
      if (total < 1) {
        testError.value = '请至少选择一种题型数量。'
        return
      }
      if (total > 10) {
        testError.value = '一次最多 10 题，请减少题目数量。'
        return
      }

      const typeList = []
      for (let i = 0; i < Number(testCounts.value.singleChoiceCount || 0); i++) typeList.push('选择题')
      for (let i = 0; i < Number(testCounts.value.multiChoiceCount || 0); i++) typeList.push('多选题')
      for (let i = 0; i < Number(testCounts.value.judgeCount || 0); i++) typeList.push('判断题')
      for (let i = 0; i < Number(testCounts.value.fillCount || 0); i++) typeList.push('填空题')

      const diff = questionForm.value.difficulty

      const specs = typeList.map((qt, idx) => {
        const kp = topics[idx % topics.length]
        let topicLabel = kp
        const sub = pickRandomSubpointLabelForKnowledgePoint(
          graphData.value.nodes,
          graphData.value.edges,
          kp
        )
        if (sub) {
          topicLabel = `${kp}：${sub}`
        }
        const topic = composeTopic(topicLabel) + `（题${idx + 1}）`
        return { topic, difficulty: diff, questionType: qt }
      })

      const chunk2 = (arr) => {
        const out = []
        for (let i = 0; i < arr.length; i += 2) out.push(arr.slice(i, i + 2))
        return out
      }

      const batches = chunk2(specs).slice(0, 5)
      if (batches.length > 5) {
        testError.value = '生成请求次数超限（最多 5 次），请减少题目数量。'
        return
      }

      const responses = await Promise.all(batches.map((items) => fetchQuestions({ items })))

      const results = []
      for (const resp of responses) {
        const data = resp && resp.data ? resp.data : resp
        const qs = Array.isArray(data?.questions) ? data.questions : []
        for (const q of qs) results.push(q)
      }

      const trimmed = results.slice(0, 10)

      testQuestions.value = (trimmed || [])
        .map((q, i) => {
          if (!q) return q
          const anchorKp = topics[i % topics.length]
          return {
            ...q,
            userKnowledgePoint: anchorKp,
            question: unescapeNewlinesSafe(q.question),
            explanation: unescapeNewlinesSafe(q.explanation),
            answer: unescapeNewlinesSafe(q.answer),
            options: Array.isArray(q.options) ? q.options.map(unescapeNewlinesSafe) : [],
            knowledge_points: Array.isArray(q.knowledge_points) ? q.knowledge_points.map(unescapeNewlinesSafe) : []
          }
        })

      testAnswers.value = testQuestions.value.map((q) => {
        if (q.question_type === '多选题') return []
        return ''
      })
    } catch (e) {
      const msg =
        e?.response?.data?.message ||
        e?.message ||
        (typeof e === 'string' ? e : '测试生成失败，请稍后重试。')
      testError.value = msg
      console.error('[generateTest] failed:', e)
    } finally {
      testLoading.value = false
    }
  }

  const submitTest = async () => {
    testError.value = ''
    if (!testQuestions.value.length) {
      testError.value = '请先生成测试。'
      return
    }
    if (!Array.isArray(testAnswers.value) || testAnswers.value.length !== testQuestions.value.length) {
      testError.value = '作答数据异常，请刷新后重试。'
      return
    }

    for (let i = 0; i < testQuestions.value.length; i++) {
      const q = testQuestions.value[i]
      const a = testAnswers.value[i]
      const qt = q?.question_type
      if (qt === '多选题') {
        if (!Array.isArray(a) || a.length === 0) {
          testError.value = `第 ${i + 1} 题：请至少选择一个选项。`
          return
        }
      } else {
        if (!String(a || '').trim()) {
          testError.value = `第 ${i + 1} 题：请填写/选择答案。`
          return
        }
      }
    }

    testLoading.value = true
    try {
      const now = new Date()
      const nowIso = now.toISOString()
      const nowDisplay = now.toLocaleString()
      const graders = testQuestions.value.map((q, idx) => {
        const studentAnswer = gradeStudentAnswer(q, testAnswers.value[idx])
        return fetchGrading({
          question: q.question,
          referenceAnswer: q.answer,
          studentAnswer,
          questionType: q.question_type,
          studentAnswerImageBase64: '',
          studentAnswerImageName: '',
          fullScore: 10
        })
      })

      const results = await Promise.all(graders)
      const perQuestionScores = results.map((r) => (r && r.data ? r.data : r)).map((d) => d || {})
      const totalScore = perQuestionScores.reduce((sum, r) => sum + Number(r.score || 0), 0)
      const fullScore = perQuestionScores.length * 10

      testResult.value = { totalScore, fullScore, perQuestionScores }
      testSubmitted.value = true

      const newWrongItems = []
      const anchorKp =
        String(selectedKnowledgePoint.value || '').trim() ||
        (Array.isArray(testForm.value.selectedPoints) && testForm.value.selectedPoints.length
          ? String(testForm.value.selectedPoints[0] || '').trim()
          : '') ||
        ''
      const nStudentQ = testQuestions.value.length
      const newLearningRecords = [
        {
          id: `lr-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
          time: nowDisplay,
          major: selectedMajorDisplay.value || selectedMajor.value || '',
          course: learningContextCourse.value || '',
          knowledgePoint: `自主生成测试 · ${anchorKp || '未标注'}${nStudentQ ? `（${nStudentQ} 题）` : ''}`,
          practiceAnchorLabel: anchorKp || '',
          score: totalScore,
          fullScore: fullScore,
          questionCount: nStudentQ
        }
      ]
      for (let i = 0; i < testQuestions.value.length; i++) {
        const q = testQuestions.value[i] || {}
        const result = perQuestionScores[i] || {}
        const score = Number(result.score || 0)
        const full = 10
        const kpUserAnchored = String(q.userKnowledgePoint || '').trim()
        const kpFromForm =
          Array.isArray(testForm.value.selectedPoints) && testForm.value.selectedPoints.length
            ? String(testForm.value.selectedPoints[i % testForm.value.selectedPoints.length] || '').trim()
            : ''
        const kpFromAi =
          Array.isArray(q.knowledge_points) && q.knowledge_points.length
            ? String(q.knowledge_points[0] || '').trim()
            : ''
        const kp = kpUserAnchored || kpFromForm || kpFromAi || ''

        if (score < full) {
          newWrongItems.push({
            id: `wb-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 7)}`,
            wrongBookSource: 'studentGenerated',
            questionType: q.question_type || '',
            options: Array.isArray(q.options) ? q.options.map(unescapeNewlinesSafe) : [],
            studentAnswerRaw: testAnswers.value[i],
            referenceAnswerRaw: q.answer,
            userOptionLetters: wrongBookChoiceLetters(q, testAnswers.value[i]),
            correctOptionLetters: wrongBookChoiceLetters(q, q.answer),
            major: selectedMajorDisplay.value || selectedMajor.value || '',
            course: learningContextCourse.value || '',
            knowledgePoint: kp || '未标注',
            question: q.question || '',
            explanation: unescapeNewlinesSafe(q.explanation) || '',
            myAnswer: resolveAnswerText(q, testAnswers.value[i]),
            answer: resolveAnswerText(q, q.answer),
            score,
            fullScore: full,
            collectedAt: nowDisplay,
            collectedAtIso: nowIso
          })
        }
      }

      learningRecords.value = [...newLearningRecords, ...(learningRecords.value || [])]
      wrongBook.value = [...newWrongItems, ...(wrongBook.value || [])]
      schedulePersistStudentState()
    } catch (e) {
      testError.value = e?.response?.data?.message || '提交失败，请稍后重试。'
    } finally {
      testLoading.value = false
    }
  }

  return {
    testTotalCount,
    resetTestState,
    generateTest,
    submitTest
  }
}
