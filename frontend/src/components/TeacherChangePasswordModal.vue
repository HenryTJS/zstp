<script setup>
import { ref } from 'vue'
import AccountSecurityPanel from './AccountSecurityPanel.vue'

defineProps({
  visible: { type: Boolean, required: true },
  currentUser: { type: Object, required: true }
})

const emit = defineEmits(['close'])
const passwordPanelRef = ref(null)
const saving = ref(false)

const handleSave = async () => {
  if (saving.value) return
  if (!passwordPanelRef.value || !passwordPanelRef.value.submitChange) return
  saving.value = true
  try {
    const ok = await passwordPanelRef.value.submitChange()
    if (ok) emit('close')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="emit('close')">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button class="modal-close" @click="emit('close')" aria-label="关闭">×</button>
        <h3>修改密码</h3>
        <div style="margin-top:12px;">
          <AccountSecurityPanel ref="passwordPanelRef" :current-user="currentUser" :embedded="true" />
        </div>
        <div style="display:flex;gap:8px;margin-top:12px;">
          <button class="match-height match-button" :disabled="saving" @click="handleSave">保存</button>
          <button class="match-height cancel-button" @click="emit('close')">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style src="./teacher-portal.css"></style>
