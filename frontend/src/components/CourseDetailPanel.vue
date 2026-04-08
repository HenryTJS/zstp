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

const emit = defineEmits(['join', 'apply', 'enter', 'save-meta', 'update:edit-form'])

const updateField = (k, v) => emit('update:edit-form', { ...props.editForm, [k]: v })
</script>

<template>
  <article class="result-card">
    <h3>课程介绍</h3>
    <p v-if="loading" class="panel-subtitle">加载中...</p>
    <p v-else-if="error" class="error-text">{{ error }}</p>
    <template v-else>
      <div class="course-detail-head">
        <img :src="detail.coverUrl" alt="" class="course-detail-cover" />
        <div>
          <h3>{{ detail.courseName }}</h3>
          <p class="panel-subtitle">{{ detail.summary }}</p>
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
        <h4>编辑课程信息</h4>
        <label class="ui-block">
          封面图 URL
          <input :value="editForm.coverUrl || ''" class="match-height" @input="updateField('coverUrl', $event.target.value)" />
        </label>
        <label class="ui-block ui-mt-8">
          简介
          <textarea :value="editForm.summary || ''" rows="3" @input="updateField('summary', $event.target.value)"></textarea>
        </label>
        <label class="ui-block ui-mt-8">
          大纲
          <textarea :value="editForm.syllabus || ''" rows="8" @input="updateField('syllabus', $event.target.value)"></textarea>
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
.course-detail-head { display: grid; grid-template-columns: 240px 1fr; gap: 14px; align-items: start; }
.course-detail-cover { width: 240px; height: 135px; object-fit: cover; border-radius: 8px; border: 1px solid #edf0f5; }
.course-syllabus { white-space: pre-wrap; margin: 0; font: inherit; background: #f8fafc; padding: 10px; border-radius: 8px; }
</style>
