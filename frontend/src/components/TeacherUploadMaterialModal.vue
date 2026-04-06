<script setup>
defineProps({
  visible: { type: Boolean, required: true },
  uploadForm: { type: Object, required: true },
  uploadTargetPoint: { type: Object, default: null },
  loading: { type: Boolean, required: true },
  message: { type: String, default: '' },
  error: { type: String, default: '' }
})

const emit = defineEmits(['close', 'file-change', 'submit'])
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="emit('close')">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button type="button" class="modal-close" @click="emit('close')" aria-label="关闭">×</button>
        <h3>上传资料 - {{ uploadForm.point || (uploadTargetPoint && uploadTargetPoint.pointName) }}</h3>
        <div class="grid-form single-col ui-mt-12">
          <label>
            资料标题
            <input v-model="uploadForm.title" class="match-height" placeholder="例如：讲义" />
          </label>
          <label>
            描述
            <textarea v-model="uploadForm.description" rows="3" placeholder="输入资料说明"></textarea>
          </label>
          <label>
            文件（可多选）
            <input type="file" multiple @change="emit('file-change', $event)" />
          </label>
        </div>
        <div class="ui-actions-row">
          <button
            type="button"
            class="match-height match-button"
            :disabled="loading || !uploadForm.files.length || !uploadForm.title"
            @click="emit('submit')"
          >
            上传
          </button>
          <button type="button" class="match-height cancel-button" @click="emit('close')">取消</button>
        </div>
        <p v-if="message" class="ok-text">{{ message }}</p>
        <p v-if="error" class="error-text">{{ error }}</p>
      </div>
    </div>
  </div>
</template>

<style src="./teacher-portal.css"></style>
