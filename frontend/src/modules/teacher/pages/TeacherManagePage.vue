<script setup>
import { computed, ref, watch } from 'vue'
import TeacherKnowledge from '../components/TeacherKnowledge.vue'
import TeacherCourseDashboard from '../components/TeacherCourseDashboard.vue'
import TeacherCourseProgress from '../components/TeacherCourseProgress.vue'
import TeacherStudentAnalytics from '../components/TeacherStudentAnalytics.vue'
import { getCourseConfig, updateCourseConfig } from '../../../api/client'

const props = defineProps({
  currentUser: { type: Object, required: true },
  selectedCourse: { type: String, default: '' },
  canConfigureCourse: { type: Boolean, default: false },
  points: { type: Array, required: true },
  materialsCount: { type: Number, default: 0 },

  selectedPointIds: { type: Array, required: true },
  pointMessage: { type: String, default: '' },
  pointError: { type: String, default: '' },
  mdImportLoading: { type: Boolean, default: false },
  mdImportError: { type: String, default: '' },
  mdImportResult: { type: String, default: '' },
  getPointNumber: { type: Function, required: true },

  downloadKnowledgePointMdTemplate: { type: Function, required: true },
  openMdImport: { type: Function, required: true },
  openAddPoint: { type: Function, required: true },
  handleDeleteSelectedPoints: { type: Function, required: true },
  openEditPoint: { type: Function, required: true },
  openUploadModal: { type: Function, required: true },
  openViewMaterials: { type: Function, required: true },
  openDiscussionPoint: { type: Function, required: true },
  openPointTest: { type: Function, required: true },
  canPublishPointTest: { type: Function, required: true },
  openAnalyticsForPoint: { type: Function, required: true },

  analyticsPointName: { type: String, default: '' }
})

const emit = defineEmits(['update:selected-point-ids', 'update:analytics-point-name'])
const analyticsDialogVisible = ref(false)
const weightDialogVisible = ref(false)

const courseConfigLoading = ref(false)
const courseConfigSaving = ref(false)
const courseConfigError = ref('')
const courseConfigMessage = ref('')
const weightForm = ref({
  logicReasoning: 0.2,
  numericCalculation: 0.2,
  semanticUnderstanding: 0.2,
  spatialImagination: 0.2,
  memoryRetrieval: 0.2
})

const fmt2 = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}
const stepRound = (v, step) => Math.round((Number(v) || 0) / step) * step
const clamp = (v, min, max) => Math.max(min, Math.min(max, v))
const setWeightFromInput = (key, raw) => {
  const n = Number(raw)
  weightForm.value[key] = Number.isFinite(n) ? n : 0
}
const normalizeSingleWeight = (key) => {
  const min = 0.05
  const step = 0.05
  const raw = Number(weightForm.value[key] || 0)
  weightForm.value[key] = clamp(stepRound(raw, step), min, 1)
}
const isStepMultiple = (v, step) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return false
  const q = n / step
  return Math.abs(q - Math.round(q)) < 1e-9
}
const validateWeightsForSave = () => {
  const keys = ['logicReasoning', 'numericCalculation', 'semanticUnderstanding', 'spatialImagination', 'memoryRetrieval']
  for (const k of keys) {
    const v = Number(weightForm.value[k] || 0)
    if (!Number.isFinite(v)) return { ok: false, message: '权重必须为数字。' }
    if (v < 0.05 - 1e-12) return { ok: false, message: '每项权重不得低于 0.05。' }
    if (!isStepMultiple(v, 0.05)) return { ok: false, message: '权重必须以 0.05 为单位调整。' }
  }
  const s = keys.reduce((sum, k) => sum + Number(weightForm.value[k] || 0), 0)
  if (Math.abs(s - 1) > 1e-9) return { ok: false, message: '五维权重之和必须等于 1。' }
  return { ok: true, message: '' }
}
const weightSumOk = computed(() => {
  const s =
    Number(weightForm.value.logicReasoning || 0) +
    Number(weightForm.value.numericCalculation || 0) +
    Number(weightForm.value.semanticUnderstanding || 0) +
    Number(weightForm.value.spatialImagination || 0) +
    Number(weightForm.value.memoryRetrieval || 0)
  return Math.abs(s - 1) < 1e-9
})
const resetWeightForm = () => {
  weightForm.value = {
    logicReasoning: 0.2,
    numericCalculation: 0.2,
    semanticUnderstanding: 0.2,
    spatialImagination: 0.2,
    memoryRetrieval: 0.2
  }
}
const loadCourseConfig = async () => {
  const courseName = String(props.selectedCourse || '').trim()
  if (!props.canConfigureCourse || !courseName || !props.currentUser?.id) {
    courseConfigError.value = ''
    courseConfigMessage.value = ''
    resetWeightForm()
    return
  }
  courseConfigLoading.value = true
  courseConfigError.value = ''
  courseConfigMessage.value = ''
  try {
    const { data } = await getCourseConfig(courseName, { teacherUserId: props.currentUser.id })
    const w = data?.weights || {}
    weightForm.value.logicReasoning = Number(w.logicReasoning ?? 0.2)
    weightForm.value.numericCalculation = Number(w.numericCalculation ?? 0.2)
    weightForm.value.semanticUnderstanding = Number(w.semanticUnderstanding ?? 0.2)
    weightForm.value.spatialImagination = Number(w.spatialImagination ?? 0.2)
    weightForm.value.memoryRetrieval = Number(w.memoryRetrieval ?? 0.2)
  } catch (e) {
    courseConfigError.value = e?.response?.data?.message || e?.message || '加载课程维度权重失败。'
    resetWeightForm()
  } finally {
    courseConfigLoading.value = false
  }
}
const saveCourseConfig = async () => {
  const courseName = String(props.selectedCourse || '').trim()
  if (!props.canConfigureCourse || !courseName || !props.currentUser?.id) return
  const v = validateWeightsForSave()
  if (!v.ok) {
    courseConfigError.value = v.message || '保存失败。'
    courseConfigMessage.value = ''
    return
  }
  courseConfigSaving.value = true
  courseConfigError.value = ''
  courseConfigMessage.value = ''
  try {
    await updateCourseConfig(courseName, {
      teacherUserId: props.currentUser.id,
      weights: { ...weightForm.value }
    })
    courseConfigMessage.value = '维度权重已保存。'
  } catch (e) {
    courseConfigError.value = e?.response?.data?.message || e?.message || '保存维度权重失败。'
  } finally {
    courseConfigSaving.value = false
  }
}
watch(
  () => [props.currentUser?.id, props.selectedCourse, props.canConfigureCourse],
  () => { void loadCourseConfig() },
  { immediate: true }
)
watch(
  () => props.selectedCourse,
  () => {
    analyticsDialogVisible.value = false
    weightDialogVisible.value = false
  }
)

const openAnalyticsDialog = (point) => {
  props.openAnalyticsForPoint(point)
  analyticsDialogVisible.value = true
}

const openWeightDialog = async () => {
  if (!props.canConfigureCourse) return
  await loadCourseConfig()
  weightDialogVisible.value = true
}
</script>

<template>
  <div id="teacher-course-dashboard-panel">
    <TeacherCourseDashboard
      :current-user="currentUser"
      :selected-course="selectedCourse"
      :points="points"
      :material-count="materialsCount"
    />
  </div>

  <div id="teacher-knowledge-panel" class="ui-mt-12">
    <TeacherKnowledge
      :points="points"
      :selected-point-ids="selectedPointIds"
      :point-message="pointMessage"
      :point-error="pointError"
      :md-import-loading="mdImportLoading"
      :md-import-error="mdImportError"
      :md-import-result="mdImportResult"
      :get-point-number="getPointNumber"
      @update:selected-point-ids="(v) => emit('update:selected-point-ids', v)"
      :on-download-template="downloadKnowledgePointMdTemplate"
      :on-open-md-import="openMdImport"
      :on-open-add-point="openAddPoint"
      :on-delete-selected-points="handleDeleteSelectedPoints"
      :on-open-edit-point="openEditPoint"
      :on-open-upload-modal="openUploadModal"
      :on-open-view-materials="openViewMaterials"
      :on-open-discussion="openDiscussionPoint"
      :on-open-point-test="openPointTest"
      :can-publish-point-test="canPublishPointTest"
      :on-open-analytics="openAnalyticsDialog"
      :on-open-course-weight-config="openWeightDialog"
    />
  </div>

  <div id="teacher-course-progress-panel" class="ui-mt-12">
    <TeacherCourseProgress :current-user="currentUser" :selected-course="selectedCourse" />
  </div>

  <div v-if="weightDialogVisible" class="modal-mask" @click.self="weightDialogVisible = false">
    <div class="modal-wrapper" style="max-width: 980px; width: 95vw">
      <div class="modal-container">
        <button class="modal-close" type="button" aria-label="关闭" @click="weightDialogVisible = false">×</button>
        <h3 class="portal-section-title">课程维度权重设置</h3>
        <p v-if="courseConfigError" class="error-text">{{ courseConfigError }}</p>
        <p v-if="courseConfigMessage" class="ok-text">{{ courseConfigMessage }}</p>
        <div class="course-weight-row ui-mt-8">
          <label>
            逻辑推理
            <input
              :value="fmt2(weightForm.logicReasoning)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              :disabled="courseConfigLoading || courseConfigSaving"
              @input="setWeightFromInput('logicReasoning', $event.target.value)"
              @change="normalizeSingleWeight('logicReasoning')"
            />
          </label>
          <label>
            数量计算
            <input
              :value="fmt2(weightForm.numericCalculation)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              :disabled="courseConfigLoading || courseConfigSaving"
              @input="setWeightFromInput('numericCalculation', $event.target.value)"
              @change="normalizeSingleWeight('numericCalculation')"
            />
          </label>
          <label>
            语义理解
            <input
              :value="fmt2(weightForm.semanticUnderstanding)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              :disabled="courseConfigLoading || courseConfigSaving"
              @input="setWeightFromInput('semanticUnderstanding', $event.target.value)"
              @change="normalizeSingleWeight('semanticUnderstanding')"
            />
          </label>
          <label>
            空间想象
            <input
              :value="fmt2(weightForm.spatialImagination)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              :disabled="courseConfigLoading || courseConfigSaving"
              @input="setWeightFromInput('spatialImagination', $event.target.value)"
              @change="normalizeSingleWeight('spatialImagination')"
            />
          </label>
          <label>
            记忆检索
            <input
              :value="fmt2(weightForm.memoryRetrieval)"
              type="number"
              min="0.05"
              max="0.8"
              step="0.05"
              class="match-height"
              :disabled="courseConfigLoading || courseConfigSaving"
              @input="setWeightFromInput('memoryRetrieval', $event.target.value)"
              @change="normalizeSingleWeight('memoryRetrieval')"
            />
          </label>
        </div>
        <div class="inline-form ui-mt-12">
          <button type="button" class="match-button" :disabled="courseConfigLoading || courseConfigSaving || !weightSumOk" @click="saveCourseConfig">
            {{ courseConfigSaving ? '保存中...' : '保存维度权重' }}
          </button>
        </div>
      </div>
    </div>
  </div>

  <div v-if="analyticsDialogVisible" class="modal-mask" @click.self="analyticsDialogVisible = false">
    <div class="modal-wrapper" style="max-width: 980px; width: 95vw">
      <div class="modal-container">
        <button class="modal-close" type="button" aria-label="关闭" @click="analyticsDialogVisible = false">×</button>
        <TeacherStudentAnalytics
          :current-user="currentUser"
          :selected-course="selectedCourse"
          :points="points"
          :point-name="analyticsPointName"
          :get-point-number="getPointNumber"
          @update:point-name="(v) => emit('update:analytics-point-name', v)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.course-weight-row{
  display:grid;
  grid-template-columns:repeat(5,minmax(0,1fr));
  gap:12px;
}
@media (max-width:1200px){
  .course-weight-row{
    grid-template-columns:repeat(3,minmax(0,1fr));
  }
}
@media (max-width:760px){
  .course-weight-row{
    grid-template-columns:repeat(2,minmax(0,1fr));
  }
}
</style>

