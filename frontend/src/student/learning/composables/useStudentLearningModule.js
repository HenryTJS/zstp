export function useStudentLearningModule({
  learningContextCourseRef,
  selectedKnowledgePointRef,
  joinedCoursesRef,
  previewUnjoinedCourseRef,
  selectedCourseRef,
  currentPageRef,
  router,
  persistStudentState,
  generatedQuestionRef,
  questionErrorRef,
  questionLoadingRef,
  practiceResultRef,
  practiceErrorRef,
  practiceAnswerRef,
  selectedChoiceAnswerRef,
  answerImageFileRef,
  answerImageBase64Ref,
  resetTestState,
  testLoadingRef,
  examErrorRef,
  testFormRef
}) {
  const enterPaperFromGraph = async () => {
    const course = String(learningContextCourseRef.value || '').trim()
    const kp = String(selectedKnowledgePointRef.value || '').trim()
    if (!course || !joinedCoursesRef.value.includes(course)) return
    previewUnjoinedCourseRef.value = ''
    learningContextCourseRef.value = course
    selectedCourseRef.value = course
    currentPageRef.value = 'paper'
    await router.push({ path: '/student/paper', query: { course, kp } })
    await persistStudentState(false)
  }

  const clearExerciseUiAfterQuittingCurrentCourse = () => {
    generatedQuestionRef.value = null
    questionErrorRef.value = ''
    questionLoadingRef.value = false
    practiceResultRef.value = null
    practiceErrorRef.value = ''
    practiceAnswerRef.value = ''
    selectedChoiceAnswerRef.value = ''
    answerImageFileRef.value = null
    answerImageBase64Ref.value = ''
    resetTestState?.()
    testLoadingRef.value = false
    examErrorRef.value = ''
    if (testFormRef.value && typeof testFormRef.value === 'object') {
      testFormRef.value.selectedPoints = []
    }
  }

  return {
    enterPaperFromGraph,
    clearExerciseUiAfterQuittingCurrentCourse
  }
}

