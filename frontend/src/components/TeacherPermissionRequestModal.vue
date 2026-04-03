<script setup>
const props = defineProps({
  visible: { type: Boolean, required: true },
  courseName: { type: String, required: true },
  requestText: { type: String, required: true },
  submitting: { type: Boolean, required: true },
  error: { type: String, required: false, default: '' }
})

const emit = defineEmits(['close', 'update:requestText', 'submit'])
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="emit('close')">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button class="modal-close" @click="emit('close')" aria-label="关闭">×</button>
        <h3>获得权限申请书 - {{ courseName }}</h3>

        <div class="grid-form single-col" style="margin-top:12px;">
          <label>
            申请内容
            <textarea
              :value="requestText"
              rows="6"
              placeholder="请写明希望获得该课程权限的理由、计划或相关经验。"
              @input="emit('update:requestText', $event.target.value)"
            ></textarea>
          </label>
        </div>

        <div style="display:flex;gap:8px;margin-top:12px;">
          <button
            class="match-height match-button"
            :disabled="submitting"
            @click="emit('submit')"
          >
            {{ submitting ? '提交中…' : '提交申请' }}
          </button>
          <button class="match-height cancel-button" @click="emit('close')" style="margin-left:8px;">取消</button>
        </div>

        <p v-if="error" class="error-text" style="margin-top:10px">{{ error }}</p>
      </div>
    </div>
  </div>
</template>

<style src="./teacher-portal.css"></style>

