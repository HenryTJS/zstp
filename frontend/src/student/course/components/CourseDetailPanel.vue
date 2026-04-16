<script setup>
import { computed, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps({
  role: { type: String, required: true },
  detail: { type: Object, required: true },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /** 授课教师展示文案（用户名，顿号分隔）；空串时显示占位文案 */
  teachersText: { type: String, default: '' },
  canAccess: { type: Boolean, default: false },
  canEditMeta: { type: Boolean, default: false }
})

const emit = defineEmits(['join', 'apply', 'enter', 'quit', 'upload-cover'])

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

const coverPickerRef = ref(null)
const canReplaceCover = computed(() => props.role === 'teacher' && props.canEditMeta)
const openCoverPicker = () => {
  if (!canReplaceCover.value) return
  coverPickerRef.value?.click()
}
const onCoverFileChange = (e) => {
  emit('upload-cover', e)
}

/** 与后端 CourseCatalogService 默认简介/大纲文案保持一致，避免接口偶发空字段时两端观感不一致 */
const nameForDefaults = computed(() => {
  const n = String(props.detail?.courseName ?? '').trim()
  return n || '本课程'
})

const defaultSummaryText = computed(() => {
  return `## 课程介绍

${nameForDefaults.value}简介：围绕课程核心知识点，强调概念理解、案例分析与实践应用。

## 课程大纲

1. 课程导论
2. 核心概念与术语
3. 方法与流程
4. 案例分析
5. 综合实践与复盘

（${nameForDefaults.value} 可按教学需要调整）`
})

const isLegacyPlainDefaultSummary = (text) => {
  const s = String(text || '').trim()
  if (!s) return false
  const courseName = String(nameForDefaults.value || '').trim()
  if (courseName && s === `${courseName}简介：围绕课程核心知识点，强调概念理解、案例分析与实践应用。`) return true
  return s.includes('简介：围绕课程核心知识点，强调概念理解、案例分析与实践应用。')
}

const markdownBody = computed(() => {
  const s = String(props.detail?.summary ?? '').trim()
  const legacy = String(props.detail?.syllabus ?? '').trim()
  if (s) return isLegacyPlainDefaultSummary(s) ? defaultSummaryText.value : s
  if (legacy) return legacy
  return defaultSummaryText.value
})

const md = new MarkdownIt({ breaks: true, linkify: true, typographer: true })
const markdownHtml = computed(() => md.render(markdownBody.value))
</script>

<template>
  <div class="course-detail-stack">
    <article class="result-card">
      <p v-if="loading" class="panel-subtitle">加载中...</p>
      <p v-else-if="error" class="error-text">{{ error }}</p>
      <template v-else>
        <input
          v-if="canReplaceCover"
          ref="coverPickerRef"
          type="file"
          accept=".png,.jpg,.jpeg,image/png,image/jpeg"
          class="course-cover-file-input"
          @change="onCoverFileChange"
        />
        <div class="course-detail-head">
          <img
            v-if="detail.coverUrl && !coverLoadFailed"
            :src="detail.coverUrl"
            alt=""
            class="course-detail-cover"
            :class="{ 'course-detail-cover--editable': canReplaceCover }"
            :title="canReplaceCover ? '点击更换封面' : ''"
            @error="onCoverError"
            @click="openCoverPicker"
          />
          <div
            v-else
            class="course-detail-cover course-detail-cover--placeholder"
            :class="{ 'course-detail-cover--editable': canReplaceCover }"
            role="img"
            :aria-label="detail.courseName ? `${detail.courseName} 封面占位` : '课程封面占位'"
            :title="canReplaceCover ? '点击上传封面' : ''"
            @click="openCoverPicker"
          >
            <span>{{ detail.courseName || '课程' }}</span>
          </div>
          <div class="course-detail-main">
            <h3 class="course-page-title">{{ detail.courseName || '课程' }}</h3>
            <p v-if="detail.courseName" class="course-teachers-line">
              授课教师：{{ teachersText || '暂无授课教师信息' }}
            </p>
          </div>
        </div>

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
      </template>
    </article>

    <article v-if="!loading && !error" class="result-card">
      <section class="course-detail-section">
        <div class="course-md-body" v-html="markdownHtml"></div>
      </section>
    </article>
  </div>
</template>

<style scoped>
.course-detail-title-row {
  display: none;
}
.course-page-title {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}
.course-detail-stack{
  display:grid;
  gap:12px;
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
.course-detail-cover--editable{
  cursor:pointer;
}
.course-detail-cover--editable:hover{
  box-shadow: 0 12px 28px rgba(2, 6, 23, 0.14);
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
  align-content: start;
}
.course-teachers-line {
  margin: 0;
  font-size: 14px;
  color: #475569;
  line-height: 1.5;
}
.course-md-body{
  color:#334155;
  line-height:1.7;
  font-size:15px;
  word-break:break-word;
}
.course-md-body :deep(h1),
.course-md-body :deep(h2),
.course-md-body :deep(h3),
.course-md-body :deep(h4){
  margin:10px 0 8px;
  color:#0f172a;
  line-height:1.35;
}
.course-md-body :deep(p),
.course-md-body :deep(ul),
.course-md-body :deep(ol){
  margin:0 0 10px;
}
.course-md-body :deep(code){
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
}
.course-cover-file-input{
  display:none;
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
@import '@/student/styles/student-portal.css';
</style>

