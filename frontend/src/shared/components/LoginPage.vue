<script setup>
import { inject, ref } from 'vue'
import { loginUser, changePassword } from '../../api/client'
import { appShellKey } from '../../appShell'

const emit = defineEmits(['login-success'])
const shell = inject(appShellKey, null)

const loading = ref(false)
const message = ref('')
const error = ref('')
const loginForm = ref({ identity: '', password: '' })
const forgotVisible = ref(false)
const forgotLoading = ref(false)
const forgotMessage = ref('')
const forgotError = ref('')
const forgotForm = ref({ username: '', workId: '', newPassword: '', confirmPassword: '' })

const doLogin = async () => {
  loading.value = true
  message.value = ''
  error.value = ''
  try {
    const { data } = await loginUser(loginForm.value)
    message.value = `登录成功，欢迎 ${data.user?.username ?? ''}`
    if (typeof shell?.loginSuccess === 'function') shell.loginSuccess(data.user)
    else emit('login-success', data.user)
  } catch (err) {
    error.value = err?.response?.data?.message || '登录失败。'
  } finally {
    loading.value = false
  }
}

const openForgot = () => {
  forgotVisible.value = true
  forgotLoading.value = false
  forgotError.value = ''
  forgotMessage.value = ''
  forgotForm.value = { username: '', workId: '', newPassword: '', confirmPassword: '' }
}

const closeForgot = () => {
  forgotVisible.value = false
}

const submitForgot = async () => {
  forgotMessage.value = ''
  forgotError.value = ''
  const username = String(forgotForm.value.username || '').trim()
  const workId = String(forgotForm.value.workId || '').trim()
  const newPassword = String(forgotForm.value.newPassword || '')
  const confirmPassword = String(forgotForm.value.confirmPassword || '')

  if (!username || !workId || !newPassword) {
    forgotError.value = '请填写姓名、学工号和新密码。'
    return
  }
  if (newPassword.length < 6) {
    forgotError.value = '新密码长度至少 6 位。'
    return
  }
  if (newPassword !== confirmPassword) {
    forgotError.value = '两次输入的新密码不一致。'
    return
  }

  forgotLoading.value = true
  try {
    await changePassword({ username, workId, newPassword })
    forgotMessage.value = '密码重置成功，请使用新密码登录。'
    forgotForm.value.newPassword = ''
    forgotForm.value.confirmPassword = ''
  } catch (err) {
    forgotError.value = err?.response?.data?.message || '重置失败，请检查姓名与学工号。'
  } finally {
    forgotLoading.value = false
  }
}
</script>

<template>
  <section class="login-shell">
    <div class="login-grid single-card">
      <article class="result-card login-card">
        <h3>账户登录</h3>
        <div class="grid-form">
          <label>
            学工号或邮箱
            <input v-model="loginForm.identity" autocomplete="username" />
          </label>
          <label>
            密码
            <input v-model="loginForm.password" type="password" />
          </label>
          <button type="button" class="match-button" :disabled="loading" @click="doLogin">登录</button>
          <button type="button" class="cancel-button" :disabled="loading" @click="openForgot">忘记密码</button>
        </div>
      </article>
    </div>

    <p v-if="message" class="ok-text">{{ message }}</p>
    <p v-if="error" class="error-text">{{ error }}</p>

    <div v-if="forgotVisible" class="modal-mask" @click.self="closeForgot">
      <div class="modal-wrapper">
        <div class="modal-container">
          <button class="modal-close" type="button" aria-label="关闭" @click="closeForgot">×</button>
          <h3>忘记密码</h3>
          <div class="grid-form">
            <label>
              姓名
              <input v-model="forgotForm.username" />
            </label>
            <label>
              学工号
              <input v-model="forgotForm.workId" />
            </label>
            <label>
              新密码
              <input v-model="forgotForm.newPassword" type="password" />
            </label>
            <label>
              确认新密码
              <input v-model="forgotForm.confirmPassword" type="password" />
            </label>
            <button type="button" class="match-button" :disabled="forgotLoading" @click="submitForgot">
              {{ forgotLoading ? '提交中…' : '重置密码' }}
            </button>
            <button type="button" class="cancel-button" :disabled="forgotLoading" @click="closeForgot">取消</button>
          </div>
          <p v-if="forgotMessage" class="ok-text">{{ forgotMessage }}</p>
          <p v-if="forgotError" class="error-text">{{ forgotError }}</p>
        </div>
      </div>
    </div>
  </section>
</template>
