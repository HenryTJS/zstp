<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  role: { type: String, required: true },
  detail: { type: Object, required: true },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /** 授课教师展示文案（用户名，顿号分隔）；空串时显示占位文案 */
  teachersText: { type: String, default: '' },
  canAccess: { type: Boolean, default: false },
  canEditMeta: { type: Boolean, default: false },
  isSubmitting: { type: Boolean, default: false },
  editForm: { type: Object, default: () => ({ coverUrl: '', summary: '', syllabus: '' }) }
})

const emit = defineEmits(['join', 'apply', 'enter', 'quit', 'save-meta', 'upload-cover', 'update:edit-form'])

const updateField = (k, v) => emit('update:edit-form', { ...props.editForm, [k]: v })

/** 默认封面走 dummyimage.com，国内网络常加载失败，避免一直显示裂图 */
const coverLoadFailed = ref(false)
watch(
  () => [props.detail?.courseName, props.detail?.coverUrl],
  () => {
    coverLoadFailed.value = false
  }
)

const onCoverError = () => {
  coverLoadFailed.value = true
}

/** 与后端 CourseCatalogService 默认简介/大纲文案保持一致，避免接口偶发空字段时两端观感不一致 */
const nameForDefaults = computed(() => {
  const n = String(props.detail?.courseName ?? '').trim()
  return n || '本课程'
})

const defaultSummaryText = computed(
  () => `${nameForDefaults.value}简介：围绕课程核心知识点，强调概念理解、案例分析与实践应用。`
)

const defaultSyllabusText = computed(
  () =>
    `1. 课程导论\n2. 核心概念与术语\n3. 方法与流程\n4. 案例分析\n5. 综合实践与复盘\n\n（${nameForDefaults.value} 可按教学需要调整）`
)

const introBody = computed(() => {
  const s = String(props.detail?.summary ?? '').trim()
  return s || defaultSummaryText.value
})

const syllabusBody = computed(() => {
  const s = String(props.detail?.syllabus ?? '').trim()
  return s || defaultSyllabusText.value
})
</script>

<template>
  <article class="result-card">
    <div class="course-detail-title-row">
      <h3
        class="course-page-title portal-section-title"
        :class="role === 'teacher' ? 'portal-section-title--sky' : 'portal-section-title--teal'"
      >
        {{ detail.courseName || '课程' }}
      </h3>
      <span class="course-role-tag">{{ role === 'teacher' ? '教师视图' : '学生视图' }}</span>
    </div>
    <p v-if="loading" class="panel-subtitle">加载中...</p>
    <p v-else-if="error" class="error-text">{{ error }}</p>
    <template v-else>
      <div class="course-detail-head ui-mt-12">
        <img
          v-if="detail.coverUrl && !coverLoadFailed"
          :src="detail.coverUrl"
          alt=""
          class="course-detail-cover"
          @error="onCoverError"
        />
        <div
          v-else
          class="course-detail-cover course-detail-cover--placeholder"
          role="img"
          :aria-label="detail.courseName ? `${detail.courseName} 封面占位` : '课程封面占位'"
        >
          <span>{{ detail.courseName || '课程' }}</span>
        </div>
        <div class="course-detail-main">
          <p v-if="detail.courseName" class="course-teachers-line">
            授课教师：{{ teachersText || '暂无授课教师信息' }}
          </p>
        </div>
      </div>

      <section class="course-detail-section ui-mt-16" aria-labelledby="course-intro-heading">
        <h4 id="course-intro-heading" class="course-section-heading">课程介绍</h4>
        <p class="course-summary">{{ introBody }}</p>
      </section>

      <section class="course-detail-section ui-mt-16" aria-labelledby="course-syllabus-heading">
        <h4 id="course-syllabus-heading" class="course-section-heading">课程大纲</h4>
        <pre class="course-syllabus">{{ syllabusBody }}</pre>
      </section>

      <div class="inline-form ui-mt-12">
        <template v-if="canAccess">
          <button type="button" class="match-button" @click="emit('enter')">进入课程</button>
          <button type="button" class="cancel-button" @click="emit('quit')">退出课程</button>
        </template>
        <template v-else>
          <button v-if="role === 'student'" type="button" class="match-button" @click="emit('join')">加入课程</button>
          <button v-else-if="role === 'teacher'" type="button" class="match-button" @click="emit('apply')">申请权限</button>
        </template>
      </div>

      <div v-if="canEditMeta" class="ui-mt-16">
        <h4 class="edit-title">编辑课程信息</h4>
        <label class="ui-block">
          上传封面图片（自动裁剪 16:9）
          <input
            type="file"
            accept=".png,.jpg,.jpeg,image/png,image/jpeg"
            class="match-height detail-input"
            @change="(e) => emit('upload-cover', e)"
          />
        </label>
        <p class="panel-subtitle ui-mt-6">支持 PNG/JPG；上传后自动中心裁剪为 16:9 并统一尺寸。</p>
        <label class="ui-block ui-mt-8">
          简介
          <textarea :value="editForm.summary || ''" rows="4" class="detail-textarea" @input="updateField('summary', $event.target.value)"></textarea>
        </label>
        <label class="ui-block ui-mt-8">
          大纲
          <textarea :value="editForm.syllabus || ''" rows="10" class="detail-textarea" @input="updateField('syllabus', $event.target.value)"></textarea>
        </label>
        <div class="inline-form ui-mt-8">
          <button type="button" class="match-button" :disabled="isSubmitting" @click="emit('save-meta')">
            {{ isSubmitting ? '保存中...' : '保存课程介绍' }}
          </button>
        </div>
      </div>
    </template>
  </article>
</template>

<style scoped>
.course-detail-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.course-page-title:not(.portal-section-title) {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}
.course-page-title.portal-section-title {
  flex: 1;
  min-width: 0;
  margin-bottom: 0;
}
.course-role-tag {
  font-size: 12px;
  color: var(--ui-accent-700);
  background: var(--ui-accent-100);
  border: 1px solid var(--ui-accent-200);
  border-radius: 999px;
  padding: 4px 10px;
  flex-shrink: 0;
}
.course-detail-head {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  align-items: start;
}
.course-detail-cover {
  width: 320px;
  height: 180px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid var(--ui-card-border);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.06);
}
.course-detail-cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 12px;
  background: linear-gradient(145deg, #eef2ff 0%, #e0e7ff 55%, #dbeafe 100%);
  color: #3730a3;
  font-weight: 700;
  font-size: 15px;
  line-height: 1.35;
  box-sizing: border-box;
}
.course-detail-main {
  display: grid;
  gap: 8px;
}
.course-teachers-line {
  margin: 0;
  font-size: 14px;
  color: #475569;
  line-height: 1.5;
}
.course-section-heading {
  margin: 0 0 10px;
  font-size: 1.05rem;
  font-weight: 700;
  color: #1e293b;
}
.course-summary {
  margin: 0;
  color: #334155;
  line-height: 1.65;
  font-size: 15px;
}
.course-syllabus {
  white-space: pre-wrap;
  margin: 0;
  font: inherit;
  background: #f8fafc;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #e6edf7;
  line-height: 1.7;
  font-size: 14px;
}
.edit-title {
  margin-bottom: 8px;
}
.detail-input {
  border-radius: 10px;
  border: 1px solid var(--ui-card-border);
}
.detail-textarea {
  width: 100%;
  border-radius: 10px;
  border: 1px solid var(--ui-card-border);
  padding: 10px 12px;
  line-height: 1.6;
  resize: vertical;
}
@media (max-width: 980px) {
  .course-detail-head {
    grid-template-columns: 1fr;
  }
  .course-detail-cover {
    width: 100%;
    height: 220px;
  }
}
</style>
<style>
@import '@/styles/student/student-portal.css';
</style>

