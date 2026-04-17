import { fetchStudentDimensionScores, fetchStudentState, saveStudentState } from '../../api/client'
import { findMajorPath } from '../utils/studentMajorTree'

/**
 * 学生端学习状态：拉取 / 防抖保存 / 维度分刷新
 */
export function useStudentPersistState({
  getUserId,
  stateHydrated,
  profileMessage,
  learningRecords,
  wrongBook,
  joinedCourses,
  totalLearningSeconds,
  selectedCourse,
  learningContextCourse,
  selectedMajor1,
  selectedMajor2,
  selectedMajor3,
  majorLevel1,
  selectedMajor,
  loadMajorLevel1,
  loadMajorLevel2,
  loadMajorLevel3,
  fetchAvailableCoursesForCurrentMajor,
  readLegacyJoinedCoursesFromLocalStorage,
  ensureCourseSelection,
  dimensionScores,
  dimensionScoresLoading,
  dimensionScoresError,
  hydrateJoiningCourses
}) {
  let stateSaveTimer = null
  let dimScoreTimer = null

  const loadDimensionScores = async () => {
    const uid = getUserId?.()
    if (!uid) return
    dimensionScoresLoading.value = true
    dimensionScoresError.value = ''
    try {
      const resp = await fetchStudentDimensionScores(uid)
      dimensionScores.value = resp?.data || null
    } catch (e) {
      dimensionScoresError.value = e?.response?.data?.message || e?.message || '维度分计算失败。'
      dimensionScores.value = null
    } finally {
      dimensionScoresLoading.value = false
    }
  }

  const scheduleRefreshDimensionScores = () => {
    if (dimScoreTimer) clearTimeout(dimScoreTimer)
    dimScoreTimer = setTimeout(() => {
      void loadDimensionScores()
    }, 600)
  }

  const persistStudentState = async (showMessage = false) => {
    if (!stateHydrated.value) {
      return
    }

    try {
      await saveStudentState({
        userId: getUserId(),
        major: selectedMajor.value || null,
        courseName: learningContextCourse.value || selectedCourse.value,
        learningRecords: learningRecords.value,
        wrongBook: wrongBook.value,
        joinedCourses: joinedCourses.value,
        totalLearningSeconds: Math.max(0, Number(totalLearningSeconds.value || 0))
      })
      if (showMessage) {
        profileMessage.value = '个人信息已保存到服务器。'
      }
    } catch {
      if (showMessage) {
        profileMessage.value = '保存失败，请稍后重试。'
      }
    }
  }

  const schedulePersistStudentState = () => {
    if (!stateHydrated.value) {
      return
    }
    if (stateSaveTimer) {
      clearTimeout(stateSaveTimer)
    }
    stateSaveTimer = setTimeout(() => {
      persistStudentState(false)
    }, 350)

    scheduleRefreshDimensionScores()
  }

  const clearPendingTimers = () => {
    if (stateSaveTimer) {
      clearTimeout(stateSaveTimer)
      stateSaveTimer = null
    }
    if (dimScoreTimer) {
      clearTimeout(dimScoreTimer)
      dimScoreTimer = null
    }
  }

  const loadStudentState = async () => {
    hydrateJoiningCourses.value = true
    let needsPushJoinedMigration = false
    try {
      const { data } = await fetchStudentState(getUserId())
      await loadMajorLevel1()
      selectedMajor1.value = ''
      selectedMajor2.value = ''
      selectedMajor3.value = ''
      if (data?.major) {
        const path = findMajorPath(majorLevel1.value, data.major)
        if (path.length >= 1) selectedMajor1.value = path[0]
        if (path.length >= 2) {
          await loadMajorLevel2(path[0])
          selectedMajor2.value = path[1]
        }
        if (path.length >= 3) {
          await loadMajorLevel3(path[1])
          selectedMajor3.value = path[2]
        }
      }
      await fetchAvailableCoursesForCurrentMajor()
      const fromServer = Array.isArray(data?.joinedCourses)
        ? data.joinedCourses.filter((x) => typeof x === 'string' && x.trim())
        : []
      const legacyJoined = readLegacyJoinedCoursesFromLocalStorage()
      const mergedJoined = [...new Set([...fromServer, ...legacyJoined])]
      needsPushJoinedMigration = legacyJoined.length > 0
      joinedCourses.value = [...new Set(mergedJoined.map((x) => String(x || '').trim()).filter(Boolean))]
      if (data?.courseName && joinedCourses.value.includes(data.courseName)) {
        selectedCourse.value = data.courseName
        learningContextCourse.value = data.courseName
      } else {
        ensureCourseSelection()
        learningContextCourse.value = ''
      }

      learningRecords.value = Array.isArray(data.learningRecords) ? data.learningRecords : []
      wrongBook.value = Array.isArray(data.wrongBook) ? data.wrongBook : []
      totalLearningSeconds.value = Math.max(0, Number(data?.totalLearningSeconds || 0))
      void loadDimensionScores()
    } catch {
      profileMessage.value = '未读取到历史学习状态，已使用默认配置。'
    } finally {
      hydrateJoiningCourses.value = false
      stateHydrated.value = true
    }
    if (needsPushJoinedMigration && joinedCourses.value.length) {
      await persistStudentState(false)
    }
  }

  return {
    loadStudentState,
    persistStudentState,
    schedulePersistStudentState,
    scheduleRefreshDimensionScores,
    loadDimensionScores,
    clearPendingTimers
  }
}
