<script setup>
const props = defineProps({
  role: { type: String, required: true },
  detail: { type: Object, required: true },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  canAccess: { type: Boolean, default: false },
  canEditMeta: { type: Boolean, default: false },
  isSubmitting: { type: Boolean, default: false },
  editForm: { type: Object, default: () => ({ coverUrl: '', summary: '', syllabus: '' }) }
})

const emit = defineEmits(['join', 'apply', 'enter', 'save-meta', 'upload-cover', 'update:edit-form'])

const updateField = (k, v) => emit('update:edit-form', { ...props.editForm, [k]: v })
</script>

<template>
  <article class="result-card">
    <div class="course-detail-title-row">
      <h3>课程介绍</h3>
      <span class="course-role-tag">{{ role === 'teacher' ? '教师视图' : '学生视图' }}</span>
    </div>
    <p v-if="loading" class="panel-subtitle">加载中...</p>
    <p v-else-if="error" class="error-text">{{ error }}</p>
    <template v-else>
      <div class="course-detail-head">
        <img :src="detail.coverUrl" alt="" class="course-detail-cover" />
        <div class="course-detail-main">
          <h3 class="course-name">{{ detail.courseName }}</h3>
          <p class="course-summary">{{ detail.summary }}</p>
        </div>
      </div>
      <div class="ui-mt-12">
        <h4>课程大纲</h4>
        <pre class="course-syllabus">{{ detail.syllabus }}</pre>
      </div>

      <div class="inline-form ui-mt-12">
        <button v-if="canAccess" type="button" class="match-button" @click="emit('enter')">进入知识图谱</button>
        <button v-else-if="role === 'student'" type="button" class="match-button" @click="emit('join')">加入课程</button>
        <button v-else-if="role === 'teacher'" type="button" class="match-button" @click="emit('apply')">申请权限</button>
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
.course-role-tag {
  font-size: 12px;
  color: var(--ui-accent-700);
  background: var(--ui-accent-100);
  border: 1px solid var(--ui-accent-200);
  border-radius: 999px;
  padding: 4px 10px;
}
.course-detail-head { display: grid; grid-template-columns: 320px 1fr; gap: 16px; align-items: start; }
.course-detail-cover {
  width: 320px;
  height: 180px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid var(--ui-card-border);
  box-shadow: 0 10px 24px rgba(0,0,0,.06);
}
.course-detail-main { display: grid; gap: 8px; }
.course-name { margin: 0; }
.course-summary { margin: 0; color: #334155; line-height: 1.65; }
.course-syllabus {
  white-space: pre-wrap;
  margin: 0;
  font: inherit;
  background: #f8fafc;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #e6edf7;
  line-height: 1.7;
}
.edit-title { margin-bottom: 8px; }
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
  .course-detail-head { grid-template-columns: 1fr; }
  .course-detail-cover { width: 100%; height: 220px; }
}
</style>
