import { ref } from 'vue'

/** 课程广场与教研工作台共用的跨模块状态（避免 composable 循环依赖） */
export function createTeacherSharedRefs() {
  return {
    myCourseCatalog: ref([])
  }
}
