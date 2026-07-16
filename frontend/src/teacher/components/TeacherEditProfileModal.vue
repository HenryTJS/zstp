<script setup>
import { ref } from 'vue'
import { uploadAvatar } from '../../api/client'
import DefaultAvatar from '../../shared/components/DefaultAvatar.vue'

const props = defineProps({
  visible: { type: Boolean, required: true },
  editProfileForm: { type: Object, required: true },
  currentUser: { type: Object, required: true },
  colleges: { type: Array, required: true }
})

const emit = defineEmits(['close', 'save', 'avatar-updated'])

const avatarUploading = ref(false)
const avatarInputRef = ref(null)

const triggerAvatarUpload = () => {
  avatarInputRef.value?.click()
}

const handleAvatarFileChange = async (e) => {
  const file = e.target?.files?.[0]
  if (!file) return
  avatarUploading.value = true
  try {
    const formData = new FormData()
    formData.append('userId', String(props.currentUser.id))
    formData.append('file', file)
    const resp = await uploadAvatar(formData)
    const avatarUrl = resp?.data?.avatarUrl
    if (avatarUrl) {
      const stored = JSON.parse(localStorage.getItem('currentUser') || '{}')
      stored.avatarUrl = avatarUrl
      localStorage.setItem('currentUser', JSON.stringify(stored))
      emit('avatar-updated', avatarUrl)
    }
  } catch (err) {
    console.error('头像上传失败', err)
    const msg = err?.response?.data?.message || err?.message || '未知错误'
    alert('头像上传失败：' + msg)
  } finally {
    avatarUploading.value = false
    if (avatarInputRef.value) avatarInputRef.value.value = ''
  }
}
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="emit('close')">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button type="button" class="modal-close" @click="emit('close')" aria-label="关闭">×</button>
        <h3 class="portal-section-title portal-section-title--violet">编辑个人资料</h3>

        <!-- 头像区域 -->
        <div class="avatar-upload-section">
          <DefaultAvatar
            :username="editProfileForm.username || currentUser.username"
            :avatar-url="currentUser.avatarUrl"
            :size="72"
          />
          <div class="avatar-upload-actions">
            <button type="button" class="nav-btn" @click="triggerAvatarUpload" :disabled="avatarUploading">
              {{ avatarUploading ? '上传中…' : '更换头像' }}
            </button>
            <input
              ref="avatarInputRef"
              type="file"
              accept="image/*"
              style="display:none"
              @change="handleAvatarFileChange"
            />
          </div>
        </div>

        <div class="grid-form single-col ui-mt-12">
          <label>
            用户名
            <input v-model="editProfileForm.username" class="match-height" />
          </label>
          <label>
            邮箱
            <input v-model="editProfileForm.email" type="email" class="match-height" />
          </label>
          <label>
            学院
            <select v-model="editProfileForm.college" class="match-height">
              <option value="">请选择学院</option>
              <option v-for="c in colleges" :key="c.code" :value="c.code">{{ c.name }}</option>
            </select>
          </label>
        </div>
        <div class="ui-actions-row">
          <button type="button" class="match-height match-button" @click="emit('save')">保存</button>
          <button type="button" class="match-height cancel-button" @click="emit('close')">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.avatar-upload-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line, #e2e8f0);
}

.avatar-upload-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>

<style>
@import '@/teacher/styles/teacher-portal.css';
</style>
