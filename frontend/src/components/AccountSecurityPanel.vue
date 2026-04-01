<script setup>
import { ref } from 'vue'
import { changePassword } from '../api/client'

const props = defineProps({
  currentUser: {
    type: Object,
    required: true
  },
  // 嵌入模式：当在父组件模态中使用时，隐藏内部提交按钮，由父组件提供底部保存/取消按钮
  embedded: {
    type: Boolean,
    default: false
  }
})

const loading = ref(false)
const message = ref('')
const error = ref('')
const form = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 返回 boolean 表示是否修改成功，供父组件 await
const submitChange = async () => {
  message.value = ''
  error.value = ''

  if (form.value.newPassword.length < 6) {
    error.value = '新密码至少需要 6 位。'
    return false
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    error.value = '两次输入的新密码不一致。'
    return false
  }

  loading.value = true
  try {
    await changePassword({
      userId: props.currentUser.id,
      currentPassword: form.value.currentPassword,
      newPassword: form.value.newPassword
    })
    form.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    message.value = '密码修改成功，请使用新密码登录。'
    return true
  } catch (err) {
    error.value = err?.response?.data?.message || '密码修改失败。'
    return false
  } finally {
    loading.value = false
  }
}

// 向父组件暴露提交方法与表单/loading，便于嵌入时由父触发提交和状态读取
defineExpose({ submitChange, form, loading, message, error })
</script>

<template>
  <template v-if="!embedded">
    <section class="panel account-panel">
      <div class="panel-header">
        <div>
          <h2>账户安全</h2>
          <p class="panel-subtitle">当前账户：{{ currentUser.username }}</p>
        </div>
      </div>

      <div class="grid-form account-form">
        <label>
          当前密码
          <input v-model="form.currentPassword" type="password" class="match-height" />
        </label>
        <label>
          新密码
          <input v-model="form.newPassword" type="password" class="match-height" />
        </label>
        <label>
          确认新密码
          <input v-model="form.confirmPassword" type="password" class="match-height" />
        </label>
        <button :disabled="loading" @click="submitChange">修改密码</button>
      </div>

      <p v-if="message" class="ok-text">{{ message }}</p>
      <p v-if="error" class="error-text">{{ error }}</p>
    </section>
  </template>

  <template v-else>
    <div>
      <div class="grid-form single-col" style="margin-top:12px;">
        <label>
          当前密码
          <input v-model="form.currentPassword" type="password" class="match-height" />
        </label>
        <label>
          新密码
          <input v-model="form.newPassword" type="password" class="match-height" />
        </label>
        <label>
          确认新密码
          <input v-model="form.confirmPassword" type="password" class="match-height" />
        </label>
      </div>

      <p v-if="message" class="ok-text" style="margin-top:8px;">{{ message }}</p>
      <p v-if="error" class="error-text" style="margin-top:8px;">{{ error }}</p>
    </div>
  </template>
</template>